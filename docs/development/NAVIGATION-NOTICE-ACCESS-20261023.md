# NAVIGATION-NOTICE-ACCESS-20261023 公告入口与实际权限

状态来源为本文，当前ACCEPTED，文档CURRENT。关联NAVIGATION-SESSION-20261022及PROTOTYPE-TEMPLATES-20261022。用户要求普通账户权限正确、页面切换正常，并继续完整业务验收。

已核查Navbar.vue、HeaderNotice/index.vue、framework-navigation.ts、RuoYiManagementConfiguration及SysNoticeController。顶部公告需要system:notice:list；普通测试账户具有项目与模板业务权限，原生系统公告权限未授予。业务权限、公告入口和原生公告权限分别按当前服务器身份核验。

公告组件按当前服务器framework/navigation返回的账户身份及实际原生权限创建。没有公告权限时不创建组件，不发送公告请求；后端权限验证保持有效。具有公告权限的超管继续读取真实公告，旧响应身份与取消检查继续采用既有实现。

NN-01：Given实际普通业务账户没有公告权限，When进入项目、模板和本人账户，Then没有公告请求和公告控件，业务会话持续有效；直接请求受限公告仍返回403。

NN-02：Given实际超管拥有原生公告读取权限，When进入项目中心，Then公告组件读取真实200响应，保留正常入口。

2026-10-23真实Chrome通过NN-01与NN-02。普通账户进入项目、模板、本人账户时没有公告组件和公告请求，业务请求错误集合为空；直接读取公告仍返回403。超管公告入口保留并取得真实200响应。证据.implementation-work/notice-access-navigation-release.log、notice-access-navigation-checks.json。前端严格类型检查、正式构建及19项后端检查通过，588项静态资源与运行制品逐字节一致。证据.implementation-work/template-notice-directory-types.log、template-notice-directory-frontend.log、template-directory-unicode-green.log、template-notice-directory-package.log、template-notice-directory-runtime.json。

普通模板编辑人员已经实际保存DRAFT，并通过中文目录查找、详情与继续修订。完整目录中文查询采用UTF-8比较实际UUID字段；模板、规则、模型中文搜索均通过真实数据库及HTTP核查。证据.implementation-work/template-definition-read-release.log、template-editor-without-approval-release.log。七个页面顺序导航及三次并发导航均通过，最终页面正确，page_errors为空；证据.implementation-work/navigation-notice-regression.log。没有执行视觉检查。
