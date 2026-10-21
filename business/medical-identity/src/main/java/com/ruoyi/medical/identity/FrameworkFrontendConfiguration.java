package com.ruoyi.medical.identity;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "local")
public class FrameworkFrontendConfiguration implements WebMvcConfigurer {
    public static final List<String> ROUTES = List.of(
        "/system/user", "/system/role", "/system/menu", "/system/dept", "/system/post",
        "/system/dict", "/system/config", "/system/notice", "/monitor/online", "/monitor/job",
        "/monitor/operlog", "/monitor/logininfor", "/monitor/server", "/monitor/cache", "/monitor/cacheList", "/monitor/druid",
        "/tool/gen", "/tool/swagger", "/tool/build", "/system/user-auth/role/{userId}",
        "/system/role-auth/user/{roleId}", "/system/dict-data/index/{dictId}",
        "/monitor/job-log/index/{jobId}", "/tool/gen-edit/index/{tableId}",
        "/access", "/access/denied", "/login", "/account", "/projects", "/projects/new", "/projects/{projectId}",
        "/member-invitations/acceptances",
        "/projects/{projectId}/profile", "/projects/{projectId}/status", "/projects/{projectId}/tasks", "/projects/{projectId}/templates", "/projects/{projectId}/uploads",
        "/projects/{projectId}/tasks/{taskId}/config",
        "/projects/{projectId}/tasks/{taskId}/field-targets",
        "/projects/{projectId}/tasks/{taskId}/field-target-capabilities",
        "/projects/{projectId}/tasks/{taskId}/node-producer-rules",
        "/projects/{projectId}/task-professional-packages",
        "/projects/{projectId}/tasks/new",
        "/projects/{projectId}/tasks/{taskId}/documents/{documentId}/write",
        "/projects/{projectId}/tasks/{taskId}/documents/{documentId}/review",
        "/sources/uploads", "/library/candidates", "/library", "/library/collections/new", "/library/collections/{collectionId}",
        "/session-recovery", "/library/collections", "/library/search", "/library/search/exact", "/library/processing", "/library/artifacts",
        "/library/versions/{sourceVersion}/exact-fields",
        "/library/versions/{sourceVersion}", "/library/versions/{sourceVersion}/rights",
        "/templates", "/templates/new", "/templates/{templateId}/approval",
        "/rules", "/rules/new", "/rules/{ruleId}", "/model-configurations", "/simulation-model-configurations",
        "/document-save-audit", "/evaluation", "/evaluation/new", "/evaluation/{evaluationId}",
        "/enterprises/members", "/enterprises/roles", "/enterprises/registrations", "/enterprises/registration-reviews", "/enterprises/registration-reviews/{registrationId}",
        "/agents/execution-policies", "/agents/execution-policies/new", "/agents/execution-policies/{versionId}",
        "/agents/model-runtime", "/agents/retention", "/agents/drafts", "/agents/publication", "/agents/context-bindings",
        "/agents/capabilities", "/agents/capabilities/new", "/agents/capabilities/{recordId}",
        "/agents", "/agents/simulation-preparation", "/agents/simulation-rules", "/agents/simulation-rules/{ruleVersion}",
        "/connections", "/scenarios", "/agents/budgets", "/agents/budgets/new", "/agents/budgets/{budgetId}",
        "/agents/budgets/{budgetId}/decisions", "/agents/budgets/{budgetId}/revisions",
        "/agents/budgets/{budgetId}/revisions/{budgetRevision}"
    );

    public FrameworkFrontendConfiguration() {
        if (!new ClassPathResource("static/index.html").exists()) {
            throw new IllegalStateException("FRAMEWORK_FRONTEND_BUILD_REQUIRED");
        }
    }

    @Override public void addViewControllers(ViewControllerRegistry registry) {
        // Spring MVC提供页面入口，Router及原框架组件负责页面操作。
        for (String route : ROUTES) registry.addViewController(route).setViewName("forward:/index.html");
        registry.addViewController("/local-auth/login.html").setViewName("forward:/index.html");
    }
}
