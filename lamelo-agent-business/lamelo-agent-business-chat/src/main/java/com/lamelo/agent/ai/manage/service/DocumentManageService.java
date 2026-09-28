package com.lamelo.agent.ai.manage.service;

import com.lamelo.agent.ai.manage.dto.DocumentIndexBuildDto;
import com.lamelo.agent.ai.manage.dto.DocumentChunkQueryDto;
import com.lamelo.agent.ai.manage.dto.DocumentChunkDetailQueryDto;
import com.lamelo.agent.ai.manage.dto.DocumentDetailQueryDto;
import com.lamelo.agent.ai.manage.dto.DocumentDeleteDto;
import com.lamelo.agent.ai.manage.dto.DocumentPageQueryDto;
import com.lamelo.agent.ai.manage.dto.DocumentStrategyConfirmDto;
import com.lamelo.agent.ai.manage.dto.DocumentStrategyPlanQueryDto;
import com.lamelo.agent.ai.manage.dto.DocumentTaskLogQueryDto;
import com.lamelo.agent.ai.manage.dto.DocumentUploadDto;
import com.lamelo.agent.ai.manage.vo.DocumentIndexBuildVo;
import com.lamelo.agent.ai.manage.vo.DocumentChunkQueryVo;
import com.lamelo.agent.ai.manage.vo.DocumentChunkDetailVo;
import com.lamelo.agent.ai.manage.vo.DocumentDeleteVo;
import com.lamelo.agent.ai.manage.vo.DocumentListItemVo;
import com.lamelo.agent.ai.manage.vo.DocumentPageQueryVo;
import com.lamelo.agent.ai.manage.vo.DocumentStrategyConfirmVo;
import com.lamelo.agent.ai.manage.vo.DocumentStrategyPlanQueryVo;
import com.lamelo.agent.ai.manage.vo.DocumentTaskLogQueryVo;
import com.lamelo.agent.ai.manage.vo.DocumentUploadVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 服务层
 * @author: 阿星不是程序员
 **/

public interface DocumentManageService {

    DocumentUploadVo upload(MultipartFile file, DocumentUploadDto dto);

    DocumentPageQueryVo queryDocumentPage(DocumentPageQueryDto dto);

    DocumentListItemVo queryDocumentDetail(DocumentDetailQueryDto dto);

    DocumentDeleteVo deleteDocument(DocumentDeleteDto dto);

    DocumentStrategyPlanQueryVo queryStrategyPlan(DocumentStrategyPlanQueryDto dto);

    DocumentStrategyConfirmVo confirmStrategy(DocumentStrategyConfirmDto dto);

    DocumentIndexBuildVo buildIndex(DocumentIndexBuildDto dto);

    DocumentChunkQueryVo queryDocumentChunks(DocumentChunkQueryDto dto);

    DocumentChunkDetailVo queryDocumentChunkDetail(DocumentChunkDetailQueryDto dto);

    DocumentTaskLogQueryVo queryTaskLogs(DocumentTaskLogQueryDto dto);
}
