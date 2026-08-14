# Spring Boot 单体 + Vue3 前后端分离开发通用规范
> 适用技术栈：Spring Boot 单体后端 + Vue 3 前端 + MyBatis-Plus
> 使用说明：新项目接入仅需修改「第一章 项目基础配置清单」，全文档规则自动适配；通用规则部分无需修改

## 一、项目基础配置清单（唯一需修改项）
所有项目专属配置集中在此处，修改后全文档对应内容自动生效：

| 配置项 | 示例值 | 说明 |
|--------|--------|------|
| 项目标识 | admin-system | 用于项目目录、包名前缀命名，全小写，短横线分隔 |
| 后端根包路径 | com.{project}.admin | Java 代码统一根包，`{project}` 替换为项目标识 |
| 数据库表前缀 | t_ | 所有业务表名统一前缀 |
| 后端服务端口 | 8080 | 单体应用唯一服务端口 |
| 软删除字段名 | is_deleted | 逻辑删除字段，默认值 0=未删除 / 1=已删除 |
| 标准时间字段 | created_at / updated_at | 统一创建时间、更新时间字段名 |
| 业务模块列表 | user(用户管理)、role(角色管理)、dataset(数据集管理) | 格式：`模块标识(模块中文名)`，为单体内部业务模块，统一在同一个应用中 |

---

## 二、项目结构规范
```
{project-name}/
├── backend/                 # Spring Boot 单体后端
│   └── src/main/java/{base_package}/
│       ├── common/         # 公共模块（统一响应、工具类、异常定义、通用常量）
│       ├── config/         # 全局配置类（MyBatis-Plus、权限、跨域等）
│       ├── {module-a}/     # 业务模块A（按配置清单扩展，每个模块内部分层）
│       │   ├── controller/ # 控制层：接口入口、参数校验
│       │   ├── service/    # 业务接口层
│       │   │   └── impl/   # 业务实现层：核心逻辑、数据转换
│       │   ├── mapper/     # 数据访问层：SQL交互
│       │   ├── entity/     # 数据库实体类
│       │   └── dto/        # 数据传输对象：入参/出参封装
│       ├── {module-b}/     # 业务模块B
│       └── ...
└── frontend/               # Vue 3 前端工程
    ├── src/
    │   ├── api/            # 接口请求层（按业务模块划分）
    │   ├── views/          # 页面视图（按业务模块划分）
    │   ├── components/     # 公共组件
    │   ├── router/         # 路由配置
    │   ├── store/          # 状态管理
    │   └── utils/          # 工具函数
    └── ...
```

---

## 三、后端 API 设计规范
### 1. URL 命名规范
所有接口统一遵循 RESTful 风格，模块前缀与配置清单的业务模块标识保持一致：

| 接口类型 | URL 格式 | 说明 | 返回类型 |
|----------|----------|------|----------|
| 分页列表 | `/api/{module}/{resource}/list` | 分页查询接口 | `IPage<T>` |
| 全量列表 | `/api/{module}/{resource}/all` | 下拉菜单等无需分页的场景 | `List<T>` |
| 单条详情 | `/api/{module}/{resource}/{id}` | 根据ID查询详情 | 单条实体DTO |
| 新增创建 | `/api/{module}/{resource}/create` | 新增数据，POST 请求 | 操作结果 |
| 更新修改 | `/api/{module}/{resource}/{id}` | 修改数据，PUT 请求 | 操作结果 |
| 删除数据 | `/api/{module}/{resource}/{id}` | 删除数据，DELETE 请求 | 操作结果 |

### 2. Controller 层规范
- 类命名：`{资源名}Controller`，驼峰命名
- 请求路径与模块、资源严格对应
- 统一返回 `Result<T>` 包装类
- 仅做参数校验、请求转发，不承载业务逻辑

示例代码：
```java
@RestController
@RequestMapping("/api/{module}/{resource}")
public class XxxController {

    @Resource
    private XxxService xxxService;

    // 分页列表查询
    @GetMapping("/list")
    public Result<IPage<XxxDTO>> list(@RequestParam(defaultValue = "1") int pageNum,
                                      @RequestParam(defaultValue = "10") int pageSize,
                                      Long userId) {
        IPage<XxxDTO> result = xxxService.list(userId, pageNum, pageSize);
        return Result.success(result);
    }

    // 全量列表查询（下拉菜单用）
    @GetMapping("/all")
    public Result<List<XxxDTO>> listAll(Long userId) {
        List<XxxDTO> result = xxxService.listAll(userId);
        return Result.success(result);
    }

    // 详情查询
    @GetMapping("/{id}")
    public Result<XxxDTO> getById(@PathVariable Long id) {
        return Result.success(xxxService.getById(id));
    }

    // 新增
    @PostMapping("/create")
    public Result<Void> create(@RequestBody XxxCreateDTO dto) {
        xxxService.create(dto);
        return Result.success();
    }

    // 更新
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody XxxUpdateDTO dto) {
        xxxService.update(id, dto);
        return Result.success();
    }

    // 删除
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        xxxService.delete(id);
        return Result.success();
    }
}
```

### 3. Service 层规范
- 接口与实现类分离，接口在 `service` 包，实现在 `service.impl` 包
- 承载全部业务逻辑，负责参数校验、实体与DTO转换、事务控制
- 数据查询默认带上用户权限过滤，禁止越权数据返回

示例代码：
```java
public interface XxxService {
    IPage<XxxDTO> list(Long userId, int pageNum, int pageSize);
    List<XxxDTO> listAll(Long userId);
    XxxDTO getById(Long id);
    void create(XxxCreateDTO dto);
    void update(Long id, XxxUpdateDTO dto);
    void delete(Long id);
}

@Service
public class XxxServiceImpl implements XxxService {

    @Resource
    private XxxMapper xxxMapper;

    @Override
    public List<XxxDTO> listAll(Long userId) {
        LambdaQueryWrapper<Xxx> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Xxx::getUserId, userId);
        wrapper.orderByDesc(Xxx::getCreatedAt);
        return xxxMapper.selectList(wrapper).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}
```

### 4. 列表排序通用规则
- 无特殊业务要求时，所有列表接口**默认按创建时间倒序返回**，保证最新数据排在最前
- 数据库字段为 `created_at` 时，SQL 默认加 `ORDER BY created_at DESC`
- Java 实体字段为 `createdAt` 时，`LambdaQueryWrapper` 默认加 `orderByDesc(Xxx::getCreatedAt)`
- 业务需要次级排序时，必须在创建时间倒序之后补充，不得违反「最新创建在前」的默认规则

---

## 四、统一响应规范
### 1. Result 类位置
```
common/src/main/java/{base_package}/common/result/Result.java
```

### 2. 标准用法
```java
// 成功响应（带数据）
return Result.success(data);
// 成功响应（无数据）
return Result.success();
// 成功响应（自定义提示）
return Result.success("操作成功");

// 错误响应（默认错误码）
return Result.error("错误信息");
// 错误响应（自定义错误码）
return Result.error(400, "参数错误");
```

### 3. 响应格式标准
#### 普通成功响应
```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

#### 分页成功响应
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [...],
    "total": 100,
    "size": 10,
    "current": 1
  }
}
```

#### 错误响应
```json
{
  "code": 400,
  "message": "参数校验失败",
  "data": null
}
```

---

## 五、权限开发规范
### 1. 功能权限要求
- 新增/修改功能时，后端接口权限码、前端权限点、菜单管理数据必须保持一致
- 后端实现需同步确认：权限码命名、权限注解使用、菜单/权限树初始化脚本、角色授权落库匹配
- 目录、菜单、按钮、角色授权的同步规则以项目主权限规范为准，本规范仅约束开发落地一致性

### 2. 数据权限要求
- 所有业务数据的读写操作，必须在后端查询条件、服务逻辑、写操作校验中落实数据范围控制
- 查询默认过滤当前用户权限范围内的数据，禁止越权访问他人数据
- 写操作（新增/修改/删除）必须校验数据归属权限，禁止越权操作

---

## 六、数据库开发规范
### 1. 表命名规则
- 统一加配置的表前缀：`{table_prefix}user`、`{table_prefix}role`、`{table_prefix}dataset`
- 多单词下划线分隔：`{table_prefix}user_role`、`{table_prefix}operation_log`
- 表名全小写，见名知意

### 2. 字段命名规则
- 全小写，多单词下划线分隔：`user_id`、`created_at`、`is_deleted`
- 必须包含通用字段：`id`（主键）、`created_at`（创建时间）、`updated_at`（更新时间）、`{软删除字段}`（逻辑删除）
- 布尔类型字段用 `is_` 前缀：`is_deleted`、`is_enabled`
- 关联外键字段命名：`{关联表名}_id`，如 `user_id`、`role_id`

### 3. MyBatis-Plus 使用规范
#### 分页查询
```java
IPage<Xxx> page = new Page<>(pageNum, pageSize);
LambdaQueryWrapper<Xxx> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(Xxx::getUserId, userId);
wrapper.like(Xxx::getName, keyword);
wrapper.orderByDesc(Xxx::getCreatedAt);
IPage<Xxx> result = xxxMapper.selectPage(page, wrapper);
```

#### 非分页查询
```java
LambdaQueryWrapper<Xxx> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(Xxx::getUserId, userId);
List<Xxx> list = xxxMapper.selectList(wrapper);
```

#### 软删除
- 全局配置 MyBatis-Plus 逻辑删除，查询、更新、删除自动带软删除条件
- 物理删除仅在特殊业务场景下显式使用，禁止默认使用物理删除

---

## 七、前端开发规范
### 1. API 模块划分
按后端业务模块划分前端API文件，与后端模块一一对应：
```
frontend/src/api/modules/
├── {module-a}/index.js     # 业务模块A接口
├── {module-b}/index.js     # 业务模块B接口
└── ...
```

### 2. API 方法命名规范
每个模块的接口方法统一命名，与后端接口一一对应：
```javascript
export const xxxApi = {
  // 分页列表
  list: (params) => request('/api/{module}/{resource}/list', { method: 'GET', params }),
  // 全量列表（下拉菜单用）
  listAll: () => request('/api/{module}/{resource}/all', { method: 'GET' }),
  // 详情查询
  get: (id) => request(`/api/{module}/{resource}/${id}`, { method: 'GET' }),
  // 新增
  create: (data) => request('/api/{module}/{resource}/create', { method: 'POST', body: data }),
  // 更新
  update: (id, data) => request(`/api/{module}/{resource}/${id}`, { method: 'PUT', body: data }),
  // 删除
  delete: (id) => request(`/api/{module}/{resource}/${id}`, { method: 'DELETE' }),
}
```

### 3. 页面调用规范
#### 下拉菜单场景（用 listAll）
```javascript
const loadOptions = async () => {
  const res = await xxxApi.listAll()
  if (res.code === 200) {
    options.value = res.data || []
  }
}
```

#### 列表页场景（用 list）
```javascript
const loadData = async () => {
  const res = await xxxApi.list({ page: 1, pageSize: 10 })
  if (res.code === 200) {
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  }
}
```

---

## 八、代码提交规范
### 1. Commit Message 标准格式
```
<type>: <subject>
<body>
```

### 2. type 类型说明
| type | 说明 |
|------|------|
| feat | 新功能开发 |
| fix | Bug修复 |
| refactor | 代码重构（无功能变更） |
| docs | 文档更新 |
| style | 代码格式调整（无逻辑变更） |
| test | 测试代码补充 |
| chore | 构建脚本、工具依赖变更 |

### 3. 提交示例
```
feat: 添加用户管理模块
- 新增用户分页查询接口
- 新增用户创建/编辑功能
- 新增用户软删除能力
Closes #123
```

---

## 九、开发注意事项
### 1. 禁止提交的内容
- 本地配置文件：`application-local.yml`、`.env.local` 等私有配置
- 编译产物：后端 `target/` 目录、前端 `dist/` 目录
- IDE 配置文件：`.idea`、`.vscode` 等个人编辑器配置
- 端口、路径等本地修改的默认配置

### 2. 前端构建命令
```bash
cd frontend
npm run dev      # 启动开发模式
npm run build    # 构建生产版本
```

### 3. 后端编译启动命令
```bash
cd backend
mvn compile             # 全量编译
mvn compile -q          # 静默编译
mvn spring-boot:run     # 本地启动应用
```
