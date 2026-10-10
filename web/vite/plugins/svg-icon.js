import VitePluginSvgSpritemap from '@spiriit/vite-plugin-svg-spritemap'

export default function createSvgIcon(isBuild) {
  // 保持已有图标身份，开发及生产资源路径交给组件维护。
  return VitePluginSvgSpritemap('./src/assets/icons/svg/*.svg', {
    prefix: 'icon-',
    output: { view: false },
    svgo: isBuild
  })
}
