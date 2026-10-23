# 登录与品牌实施检查

模式为IMPLEMENT，范围为用户要求的登录、首页、共享导航、浏览器页签四项品牌使用位置。需求与任务状态来源为docs/development/LOGIN-BRAND-ACCEPTANCE-20261021.md。共享组件保留原有路由、权限与数据流程。

名称和资源路径集中在web/branding.mjs。图像使用内置imagegen生成的1254×1254透明PNG，当前项目资产为web/public/assets/brandmark.png。资产保持原始比例，页面根据使用位置设置尺寸。颜色、字体、输入、按钮、状态与响应式规范沿用任务记录中的蓝灰设计变量。

真实Chrome156验证登录页面的375、820、1440px视口及200%缩放，读取Logo资源和图片尺寸，检查密码显示、空表单、两次真实登录、首页与导航品牌、当前用户名和PLATFORM_ADMIN、注销及浏览器存储。结果保存于.implementation-work/login-brand-browser.json，页面错误和失败请求为空。

用户禁止主动视觉功能，因此未执行截图或视觉识别；DOM、计算样式、图片格式与HTTP检查不代表视觉检查。没有新增业务页面、插画、弹窗或字体，完整产品设计稿和PDF不属于本次局部实施范围。image-generation-manifest中的来源和输出均引用已复制到项目的原始生成资产，未进行图像编辑。
