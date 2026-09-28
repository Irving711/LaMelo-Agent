"""Rebuild Qdrant indexes from MySQL, optionally reusing pgvector embeddings."""

import argparse
import hashlib
import json
import os
import re
import sys
import uuid
import zlib
from collections import defaultdict
from pathlib import Path
from urllib.parse import unquote, urlparse

import pymysql
import psycopg2
import requests


TOKEN = re.compile(r"[\u3400-\u9fff]+|[A-Za-z0-9._-]+")
BATCH = 100


def required(name):
    value = os.getenv(name)
    if not value:
        raise ValueError(f"Missing environment variable: {name}")
    return value


def sparse(fields):
    values = defaultdict(float)
    for text, weight in fields:
        if not text or weight <= 0:
            continue
        for match in TOKEN.finditer(text.lower()):
            token = match.group()
            terms = ([token] if len(token) == 1 else
                     [token[i:i + 2] for i in range(len(token) - 1)]) if "\u3400" <= token[0] <= "\u9fff" else [token]
            for term in terms:
                values[zlib.crc32(term.encode("utf-8")) & 0xffffffff] += weight
    indices = list(values)
    return {"indices": indices, "values": [values[index] for index in indices]}


def stable_route_id(route_id):
    return str(uuid.UUID(bytes=hashlib.md5(route_id.encode("utf-8")).digest(), version=3))


def mysql_connection():
    parsed = urlparse(required("LAMELO_MIGRATE_MYSQL_DSN"))
    if parsed.scheme != "mysql" or not parsed.path.strip("/"):
        raise ValueError("LAMELO_MIGRATE_MYSQL_DSN must be mysql://user:pass@host:port/database")
    return pymysql.connect(host=parsed.hostname, port=parsed.port or 3306,
                           user=unquote(parsed.username or ""), password=unquote(parsed.password or ""),
                           database=unquote(parsed.path.strip("/")), charset="utf8mb4",
                           cursorclass=pymysql.cursors.DictCursor)


def query_pages(connection, sql, params=()):
    last_id = 0
    while True:
        with connection.cursor() as cursor:
            cursor.execute(sql, (*params, last_id, BATCH))
            rows = cursor.fetchall()
        if not rows:
            break
        yield rows
        last_id = rows[-1]["id"]


class Qdrant:
    def __init__(self, endpoint, api_key):
        self.endpoint = endpoint.rstrip("/")
        self.session = requests.Session()
        if api_key:
            self.session.headers["api-key"] = api_key

    def call(self, method, path, body=None, allow_404=False):
        response = self.session.request(method, self.endpoint + path, json=body, timeout=30)
        if allow_404 and response.status_code == 404:
            return None
        response.raise_for_status()
        result = response.json()
        if result.get("status") not in (None, "ok"):
            raise RuntimeError(f"Qdrant returned an error for {path}: {result.get('status')}")
        return result

    def preflight(self):
        root = self.call("GET", "/")
        version = root.get("version", "0.0").split(".")
        if tuple(int(part) for part in version[:2]) < (1, 10):
            raise RuntimeError("Qdrant 1.10 or newer is required for sparse IDF and Query API")

    def create(self, name, kind):
        if self.call("GET", f"/collections/{name}", allow_404=True):
            return
        body = {"vectors": {"dense": {"size": 1024, "distance": "Cosine", "on_disk": True}} if kind == "chunks" else {},
                "sparse_vectors": {"lexical": {"index": {"on_disk": True}, "modifier": "idf"}},
                "on_disk_payload": True, "hnsw_config": {"on_disk": True}}
        self.call("PUT", f"/collections/{name}", body)
        fields = {"chunks": {"documentId": "integer", "taskId": "integer",
                             "structureNodeId": "integer", "itemIndex": "integer"},
                  "navigation": {"documentId": "integer", "nodeType": "keyword"},
                  "routes": {"documentId": "integer", "entityType": "keyword"}}[kind]
        for field, schema in fields.items():
            self.call("PUT", f"/collections/{name}/index?wait=true",
                      {"field_name": field, "field_schema": schema})

    def upsert(self, name, points):
        if points:
            self.call("PUT", f"/collections/{name}/points?wait=true", {"points": points})

    def count(self, name):
        return self.call("POST", f"/collections/{name}/points/count", {"exact": True})["result"]["count"]

    def point_ids(self, name):
        ids = set()
        offset = None
        while True:
            body = {"limit": 500, "with_payload": False, "with_vector": False}
            if offset is not None:
                body["offset"] = offset
            result = self.call("POST", f"/collections/{name}/points/scroll", body)["result"]
            ids.update(str(point["id"]) for point in result["points"])
            offset = result.get("next_page_offset")
            if offset is None:
                return ids

    def dense_vector(self, name, point_id):
        body = {"ids": [point_id], "with_payload": False, "with_vector": True}
        points = self.call("POST", f"/collections/{name}/points", body)["result"]
        if not points:
            raise RuntimeError(f"Missing checkpoint sample point: {point_id}")
        return points[0]["vector"]["dense"]

    def sample_query(self, name, text, field, value):
        body = {"query": sparse([(text, 1)]), "using": "lexical", "limit": 1,
                "filter": {"must": [{"key": field, "match": {"value": value}}]}}
        points = self.call("POST", f"/collections/{name}/points/query", body)["result"]["points"]
        if not points:
            raise RuntimeError(f"Filtered sparse sample query returned no points: {name}")

    def sample_dense_query(self, name, vector, document_id):
        body = {"query": vector, "using": "dense", "limit": 1,
                "filter": {"must": [{"key": "documentId", "match": {"value": document_id}}]}}
        points = self.call("POST", f"/collections/{name}/points/query", body)["result"]["points"]
        if not points:
            raise RuntimeError("Filtered dense sample query returned no points")

    def switch_aliases(self, names):
        current = self.call("GET", "/aliases")["result"]["aliases"]
        old = {row["alias_name"]: row["collection_name"] for row in current}
        actions = []
        for alias, physical in names.items():
            if old.get(alias) == physical:
                continue
            if alias in old:
                actions.append({"delete_alias": {"alias_name": alias}})
            actions.append({"create_alias": {"alias_name": alias, "collection_name": physical}})
        if actions:
            self.call("POST", "/collections/aliases", {"actions": actions})
        return old


def existing_vectors(pg, ids):
    if pg is None:
        return {}
    with pg.cursor() as cursor:
        cursor.execute("SELECT id, embedding::text, embedding_model FROM public.lamelo_agent_document_embedding "
                       "WHERE status = 1 AND id = ANY(%s)", (ids,))
        return {row[0]: (json.loads(row[1]), row[2]) for row in cursor.fetchall()}


def embed(texts):
    response = requests.post(os.getenv("LAMELO_MIGRATE_EMBEDDING_URL", "https://api.siliconflow.cn/v1/embeddings"),
                             headers={"Authorization": "Bearer " + required("LAMELO_MIGRATE_EMBEDDING_API_KEY")},
                             json={"model": os.getenv("LAMELO_MIGRATE_EMBEDDING_MODEL", "BAAI/bge-m3"),
                                   "input": texts}, timeout=90)
    response.raise_for_status()
    data = sorted(response.json()["data"], key=lambda item: item["index"])
    return [item["embedding"] for item in data]


def chunk_points(rows, pg, report):
    vectors = existing_vectors(pg, [row["id"] for row in rows])
    missing = []
    for row in rows:
        vector = vectors.get(row["id"])
        if vector and len(vector[0]) == 1024 and vector[1] == os.getenv("LAMELO_MIGRATE_EMBEDDING_MODEL", "BAAI/bge-m3"):
            row["_embedding"] = vector[0]
            report["reused_pg_vectors"] += 1
        else:
            missing.append(row)
    for start in range(0, len(missing), 10):
        part = missing[start:start + 10]
        generated = embed([row["chunkText"] for row in part])
        if len(generated) != len(part) or any(len(vector) != 1024 for vector in generated):
            raise RuntimeError("Embedding response count or dimension mismatch")
        for row, vector in zip(part, generated):
            row["_embedding"] = vector
            report["new_embeddings"] += 1
    result = []
    for row in rows:
        payload = {key: row.get(key) for key in ("documentId", "taskId", "planId", "parentBlockId",
                   "chunkNo", "sourceType", "sectionPath", "structureNodeId", "structureNodeType",
                   "canonicalPath", "itemIndex", "chunkText")}
        result.append({"id": row["id"], "vector": {"dense": row["_embedding"],
                       "lexical": sparse([(row["chunkText"], 1), (row["sectionPath"], 3),
                                          (row["canonicalPath"], 2)])}, "payload": payload})
    return result


def nav_points(rows):
    type_names = {1: "DOCUMENT", 2: "SECTION", 3: "STEP", 4: "LIST_ITEM"}
    result = []
    for row in rows:
        payload = {"nodeId": row["id"], "documentId": row["documentId"],
                   "parseTaskId": row["parseTaskId"], "nodeType": type_names.get(row["nodeType"], ""),
                   "nodeCode": row["nodeCode"] or "", "nodeNo": row["nodeNo"], "depth": row["depth"],
                   "parentNodeId": row["parentNodeId"], "title": row["title"] or "",
                   "anchorText": row["anchorText"] or "", "sectionPath": row["sectionPath"] or "",
                   "canonicalPath": row["canonicalPath"] or "", "contentText": row["contentText"] or "",
                   "itemIndex": row["itemIndex"]}
        result.append({"id": row["id"], "vector": {"lexical": sparse([
            (payload["title"], 10), (payload["sectionPath"], 8),
            (payload["anchorText"], 5), (payload["contentText"], 1)])}, "payload": payload})
    return result


def route_points(connection):
    sources = [
        ("scope", "SELECT id, scope_code AS code, scope_name AS name, NULL AS documentId, "
         "scope_code AS scopeCode, NULL AS topicCode, description, aliases, examples "
         "FROM lamelo_agent_knowledge_scope_node WHERE status=1 AND id>%s ORDER BY id LIMIT %s"),
        ("topic", "SELECT id, topic_code AS code, topic_name AS name, NULL AS documentId, "
         "scope_code AS scopeCode, topic_code AS topicCode, description, aliases, examples "
         "FROM lamelo_agent_knowledge_topic_node WHERE status=1 AND id>%s ORDER BY id LIMIT %s"),
        ("document", "SELECT id, CAST(id AS CHAR) AS code, document_name AS name, id AS documentId, "
         "knowledge_scope_code AS scopeCode, NULL AS topicCode, business_category AS description, "
         "document_tags AS aliases, NULL AS examples FROM lamelo_agent_document "
         "WHERE status=1 AND index_status=3 AND last_index_task_id IS NOT NULL "
         "AND id>%s ORDER BY id LIMIT %s")]
    for entity_type, sql in sources:
        for rows in query_pages(connection, sql):
            points = []
            for row in rows:
                route_id = entity_type + ":" + row["code"]
                payload = {"routeId": route_id, "entityCode": row["code"], "entityType": entity_type,
                           "documentId": row["documentId"], "scopeCode": row["scopeCode"] or "",
                           "topicCode": row["topicCode"] or "", "displayName": row["name"],
                           "documentName": row["name"] if entity_type == "document" else ""}
                text = " ".join(str(row[key] or "") for key in ("name", "description", "aliases", "examples"))
                points.append({"id": stable_route_id(route_id), "vector": {"lexical": sparse([
                    (row["name"], 10), (row["aliases"], 8), (row["examples"], 6),
                    (row["description"], 3), (text, 4)])}, "payload": payload})
            yield points


def elasticsearch_ids(endpoint, index, auth):
    response = requests.post(endpoint.rstrip("/") + f"/{index}/_search?scroll=1m",
                             auth=auth, json={"size": 500, "_source": False,
                                              "sort": ["_doc"], "query": {"match_all": {}}}, timeout=30)
    response.raise_for_status()
    page = response.json()
    scroll_id = page.get("_scroll_id")
    ids = set()
    try:
        while True:
            hits = page["hits"]["hits"]
            if not hits:
                break
            ids.update(hit["_id"] for hit in hits)
            response = requests.post(endpoint.rstrip("/") + "/_search/scroll", auth=auth,
                                     json={"scroll": "1m", "scroll_id": scroll_id}, timeout=30)
            response.raise_for_status()
            page = response.json()
            scroll_id = page.get("_scroll_id", scroll_id)
    finally:
        if scroll_id:
            requests.delete(endpoint.rstrip("/") + "/_search/scroll", auth=auth,
                            json={"scroll_id": [scroll_id]}, timeout=30)
    return ids


def compare_elasticsearch(report, expected_ids, allow_mismatch):
    endpoint = os.getenv("LAMELO_MIGRATE_ES_URL")
    if not endpoint:
        report["es_comparison_unavailable"] = 1
        if not allow_mismatch:
            raise RuntimeError("Elasticsearch comparison unavailable; review and pass --allow-source-mismatch")
        return
    user = os.getenv("LAMELO_MIGRATE_ES_USER")
    auth = (user, os.getenv("LAMELO_MIGRATE_ES_PASSWORD", "")) if user else None
    prefix = os.getenv("LAMELO_MIGRATE_COLLECTION_PREFIX", "lamelo-agent-prod")
    indexes = [(f"{prefix}-document-keyword", "chunks"),
               (f"{prefix}-document-navigation", "navigation"),
               (f"{prefix}-knowledge-route", "routes")]
    for index, key in indexes:
        ids = elasticsearch_ids(endpoint, index, auth)
        missing = expected_ids[key] - ids
        source_only = ids - expected_ids[key]
        report["es_" + key] = len(ids)
        report["es_" + key + "_missing_current"] = len(missing)
        report["es_" + key + "_source_only"] = len(source_only)
        report["es_" + key + "_missing_examples"] = list(sorted(missing))[:10]
        report["es_" + key + "_source_only_examples"] = list(sorted(source_only))[:10]
        if (missing or source_only) and not allow_mismatch:
            raise RuntimeError(f"Elasticsearch/MySQL ID mismatch for {key}: "
                               f"missing={list(sorted(missing))[:10]}, source_only={list(sorted(source_only))[:10]}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suffix", required=True, help="Stable version suffix, e.g. 20260928")
    parser.add_argument("--apply", action="store_true", help="Write versioned Qdrant collections")
    parser.add_argument("--switch-aliases", action="store_true", help="Activate validated collections")
    parser.add_argument("--writes-paused", action="store_true", help="Confirm document writes remain paused through cutover")
    parser.add_argument("--allow-source-mismatch", action="store_true")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_-]+", args.suffix):
        parser.error("--suffix may contain only letters, numbers, _ and -")
    if args.switch_aliases and not args.apply:
        parser.error("--switch-aliases requires --apply")
    if args.switch_aliases and not args.writes_paused:
        parser.error("--switch-aliases requires --writes-paused")
    mysql = mysql_connection()
    pg = psycopg2.connect(os.getenv("LAMELO_MIGRATE_PG_DSN")) if os.getenv("LAMELO_MIGRATE_PG_DSN") else None
    qdrant = Qdrant(os.getenv("LAMELO_MIGRATE_QDRANT_URL", "http://114.55.148.51:6333"),
                    os.getenv("LAMELO_MIGRATE_QDRANT_API_KEY", ""))
    qdrant.preflight()
    prefix = os.getenv("LAMELO_MIGRATE_COLLECTION_PREFIX", "lamelo-agent-prod")
    names = {f"{prefix}-document-chunks": f"{prefix}-document-chunks-{args.suffix}",
             f"{prefix}-document-navigation": f"{prefix}-document-navigation-{args.suffix}",
             f"{prefix}-knowledge-route": f"{prefix}-knowledge-route-{args.suffix}"}
    aliases = list(names)
    checkpoint_path = Path("docs") / f"qdrant-migration-{args.suffix}.checkpoint.json"
    checkpoint = json.loads(checkpoint_path.read_text(encoding="utf-8")) if args.apply and checkpoint_path.exists() else {}
    if checkpoint and checkpoint.get("collections") != names:
        raise RuntimeError("Checkpoint belongs to a different Qdrant collection set")

    def record_progress(kind, last_id):
        checkpoint["collections"] = names
        checkpoint[kind] = last_id
        checkpoint_path.parent.mkdir(parents=True, exist_ok=True)
        pending = checkpoint_path.with_suffix(".tmp")
        pending.write_text(json.dumps(checkpoint, indent=2), encoding="utf-8")
        pending.replace(checkpoint_path)

    report = defaultdict(int)
    report["source_mismatch_acknowledged"] = int(args.allow_source_mismatch)
    expected_ids = {"chunks": set(), "navigation": set(), "routes": set()}
    samples = {}
    dense_sample = None
    first_chunk_id = None
    if args.apply:
        for kind, physical in zip(("chunks", "navigation", "routes"), names.values()):
            qdrant.create(physical, kind)
    chunk_sql = ("SELECT c.id, c.document_id AS documentId, c.task_id AS taskId, c.plan_id AS planId, "
                 "c.parent_block_id AS parentBlockId, c.chunk_no AS chunkNo, c.source_type AS sourceType, "
                 "c.section_path AS sectionPath, c.structure_node_id AS structureNodeId, "
                 "c.structure_node_type AS structureNodeType, c.canonical_path AS canonicalPath, "
                 "c.item_index AS itemIndex, c.chunk_text AS chunkText "
                 "FROM lamelo_agent_document_chunk c JOIN lamelo_agent_document d ON d.id=c.document_id "
                 "WHERE c.status=1 AND d.status=1 AND d.index_status=3 AND c.task_id=d.last_index_task_id "
                 "AND c.chunk_text IS NOT NULL AND c.id>%s ORDER BY c.id LIMIT %s")
    nav_sql = ("SELECT n.id, n.document_id AS documentId, n.parse_task_id AS parseTaskId, "
               "n.node_type AS nodeType, n.node_code AS nodeCode, n.node_no AS nodeNo, n.depth, "
               "n.parent_node_id AS parentNodeId, n.title, n.anchor_text AS anchorText, "
               "n.section_path AS sectionPath, n.canonical_path AS canonicalPath, "
               "n.content_text AS contentText, n.item_index AS itemIndex "
               "FROM lamelo_agent_document_structure_node n JOIN lamelo_agent_document d ON d.id=n.document_id "
               "WHERE n.status=1 AND d.status=1 AND n.parse_task_id=d.last_parse_task_id "
               "AND n.id>%s ORDER BY n.id LIMIT %s")
    for rows in query_pages(mysql, chunk_sql):
        report["chunks"] += len(rows)
        expected_ids["chunks"].update(str(row["id"]) for row in rows)
        if "chunks" not in samples:
            samples["chunks"] = (rows[0]["chunkText"], "documentId", rows[0]["documentId"])
            first_chunk_id = rows[0]["id"]
        if args.apply:
            if rows[-1]["id"] <= checkpoint.get("chunks", 0):
                continue
            points = chunk_points(rows, pg, report)
            if dense_sample is None and points:
                dense_sample = (points[0]["vector"]["dense"], points[0]["payload"]["documentId"])
            qdrant.upsert(names[aliases[0]], points)
            record_progress("chunks", rows[-1]["id"])
    for rows in query_pages(mysql, nav_sql):
        report["navigation"] += len(rows)
        expected_ids["navigation"].update(str(row["id"]) for row in rows)
        for row in rows:
            if row["nodeType"] == 2 and row["title"] and "navigation" not in samples:
                samples["navigation"] = (row["title"], "documentId", row["documentId"])
        if args.apply:
            if rows[-1]["id"] <= checkpoint.get("navigation", 0):
                continue
            qdrant.upsert(names[aliases[1]], nav_points(rows))
            record_progress("navigation", rows[-1]["id"])
    for points in route_points(mysql):
        report["routes"] += len(points)
        expected_ids["routes"].update(point["payload"]["routeId"] for point in points)
        if points and "routes" not in samples:
            samples["routes"] = (points[0]["payload"]["displayName"], "entityType",
                                 points[0]["payload"]["entityType"])
        if args.apply:
            qdrant.upsert(names[aliases[2]], points)
    if args.apply:
        for alias, key in zip(aliases, ("chunks", "navigation", "routes")):
            actual = qdrant.count(names[alias])
            report[key + "_qdrant"] = actual
            if actual != report[key]:
                raise RuntimeError(f"Count mismatch for {alias}: MySQL={report[key]}, Qdrant={actual}")
            expected = ({stable_route_id(route_id) for route_id in expected_ids[key]}
                        if key == "routes" else expected_ids[key])
            actual_ids = qdrant.point_ids(names[alias])
            missing = expected - actual_ids
            extra = actual_ids - expected
            if missing or extra:
                raise RuntimeError(f"Qdrant/MySQL ID mismatch for {key}: "
                                   f"missing={list(sorted(missing))[:10]}, extra={list(sorted(extra))[:10]}")
            if key in samples:
                qdrant.sample_query(names[alias], *samples[key])
        if dense_sample is None and first_chunk_id is not None:
            dense_sample = (qdrant.dense_vector(names[aliases[0]], first_chunk_id),
                            samples["chunks"][2])
        if dense_sample:
            qdrant.sample_dense_query(names[aliases[0]], *dense_sample)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if pg:
        pg_ids = set()
        with pg.cursor(name="qdrant_migration_ids") as cursor:
            cursor.itersize = 1000
            cursor.execute("SELECT id FROM public.lamelo_agent_document_embedding WHERE status=1")
            for row in cursor:
                pg_ids.add(str(row[0]))
        missing = expected_ids["chunks"] - pg_ids
        source_only = pg_ids - expected_ids["chunks"]
        report["pg_active_vectors"] = len(pg_ids)
        report["pg_missing_current"] = len(missing)
        report["pg_source_only"] = len(source_only)
        report["pg_missing_examples"] = list(sorted(missing))[:10]
        report["pg_source_only_examples"] = list(sorted(source_only))[:10]
        if (missing or source_only) and args.switch_aliases and not args.allow_source_mismatch:
            raise RuntimeError(f"PG/MySQL ID mismatch: missing={list(sorted(missing))[:10]}, "
                               f"source_only={list(sorted(source_only))[:10]}")
    else:
        report["pg_comparison_unavailable"] = 1
        if args.switch_aliases and not args.allow_source_mismatch:
            raise RuntimeError("PG comparison unavailable; pass --allow-source-mismatch after manual review")
    compare_elasticsearch(report, expected_ids, args.allow_source_mismatch or not args.switch_aliases)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if args.switch_aliases:
        previous = qdrant.switch_aliases(names)
        print(json.dumps({"previous_alias_targets": previous, "active_alias_targets": names}, indent=2))
    mysql.close()
    if pg:
        pg.close()


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, requests.RequestException, pymysql.MySQLError, psycopg2.Error) as error:
        print(f"Migration failed: {error}", file=sys.stderr)
        sys.exit(1)
