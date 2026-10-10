import router from '@/router'
import cache from '@/plugins/cache'
import { ElMessageBox, } from 'element-plus'
import { login, logout, getInfo } from '@/api/login'
import { getToken, setToken, removeToken } from '@/utils/auth'
import { isHttp, isEmpty } from "@/utils/validate"
import useLockStore from '@/store/modules/lock'
import defAva from '@/assets/images/profile.jpg'
import { loginAccount, logoutAccount, readOptions, readSession, selectEnterprise } from '@/api/medical/session'
import { fail } from '@/api/medical/protocol'
import usePermissionStore from '@/store/modules/permission'
import { beginCredential, changeCredential, clearCredential, readPendingCredential } from '@/api/medical/account'
import { readFrameworkNavigation } from '@/api/medical/framework-navigation'
import { beginOwnProfile, changeOwnProfile, clearOwnProfile, readPendingOwnProfile } from '@/api/medical/account'

let medicalRead = null

const useUserStore = defineStore(
  'user',
  {
    state: () => ({
      token: getToken(),
      id: '',
      name: '',
      nickName: '',
      avatar: '',
      roles: [],
      permissions: [],
      medicalMode: false,
      medicalSession: null,
      frameworkNavigation: null,
      medicalOptions: null,
      medicalGeneration: 0,
      medicalLoading: false,
      medicalWriting: false,
      medicalError: null,
      medicalPendingSelection: null,
      medicalPendingCredential: null,
      medicalPendingProfile: null
    }),
    actions: {
      invalidateMedical() {
        this.medicalGeneration += 1
        if (medicalRead !== null) medicalRead.abort()
        medicalRead = null
        this.medicalSession = null
        this.frameworkNavigation = null
        this.medicalOptions = null
        this.medicalLoading = false
        this.name = ''; this.nickName = ''; this.id = ''; this.avatar = ''
        this.roles = []; this.permissions = []
        usePermissionStore().setMedicalRoutes(false)
      },
      async readMedical() {
        this.medicalMode = true
        this.invalidateMedical()
        this.medicalError = null
        this.medicalLoading = true
        const generation = this.medicalGeneration
        const controller = new AbortController()
        medicalRead = controller
        try {
          const session = await readSession(controller.signal)
          const options = await readOptions(controller.signal)
          const navigation = session.user.username !== null && session.user.platform_role !== null
            ? await readFrameworkNavigation(session, controller.signal) : null
          if (generation !== this.medicalGeneration || document.hidden) return null
          if (session.user.user_id !== options.user_id || (session.current_context?.tenant_id ?? null) !== options.selected_tenant_id
              || (session.current_context?.context_revision ?? '0') !== options.expected_context_revision
              || JSON.stringify(session.memberships) !== JSON.stringify(options.memberships)) {
            fail('CONTEXT_CHANGED', '会话与企业候选已经变化，请重新读取会话')
          }
          this.medicalSession = session
          this.frameworkNavigation = navigation
          this.medicalOptions = options
          this.medicalPendingCredential = readPendingCredential(session.user.user_id)
          this.medicalPendingProfile = readPendingOwnProfile(session.user.user_id)
          this.id = session.user.user_id
          this.name = session.user.username ?? session.user.display_name ?? session.user.user_id
          this.nickName = session.user.display_name ?? this.name
          this.roles = navigation?.roles ?? []
          this.permissions = navigation?.permissions ?? []
          usePermissionStore().setMedicalRoutes(true, navigation?.routers ?? [])
          // 写入页面使用当前 Pinia 会话引用核查结果是否仍属于本次操作。
          return this.medicalSession
        } finally { if (generation === this.medicalGeneration) this.medicalLoading = false }
      },
      async loginMedical(username, password) {
        if (this.medicalWriting) throw new Error('账户请求正在执行')
        this.medicalWriting = true
        this.medicalError = null
        try {
          await loginAccount(username, password)
          await this.readMedical()
        } finally { this.medicalWriting = false }
      },
      async logoutMedical() {
        if (this.medicalWriting || this.medicalSession === null) throw new Error('当前账户无法执行注销')
        const session = this.medicalSession
        this.medicalWriting = true
        this.invalidateMedical()
        try {
          await logoutAccount(session)
          this.medicalPendingSelection = null
          window.dispatchEvent(new Event('medical-context-invalidated'))
        } finally { this.medicalWriting = false }
      },
      async selectMedical(tenantId) {
        if (this.medicalWriting || this.medicalSession === null || this.medicalOptions === null) throw new Error('当前企业选择未取得有效会话')
        const session = this.medicalSession
        const options = this.medicalOptions
        if (this.medicalPendingSelection !== null && (this.medicalPendingSelection.tenantId !== tenantId
            || this.medicalPendingSelection.actor !== session.user.user_id)) fail('IDEMPOTENCY_CONFLICT', '原企业选择仍待核查，请保留原目标和请求身份')
        if (this.medicalPendingSelection === null) this.medicalPendingSelection = { tenantId, actor: session.user.user_id,
          key: crypto.randomUUID(), revision: options.expected_context_revision }
        const pending = this.medicalPendingSelection
        this.medicalWriting = true
        this.invalidateMedical()
        try {
          const selected = await selectEnterprise(session, options, pending.tenantId, pending.key, pending.revision)
          const current = await this.readMedical()
          if (current?.current_context === null || current === null
              || JSON.stringify(current.current_context) !== JSON.stringify(selected)) fail('CONTEXT_CHANGED', '企业选择已经提交，当前结果仍需核查')
          this.medicalPendingSelection = null
          window.dispatchEvent(new Event('medical-context-invalidated'))
        } finally { this.medicalWriting = false }
      },
      async changeMedicalCredential(account, currentPassword, password) {
        if (this.medicalWriting || this.medicalSession === null || account.user_id !== this.medicalSession.user.user_id) {
          fail('ACCESS_DENIED', '本人密码操作没有取得有效会话')
        }
        const session = this.medicalSession
        const pending = beginCredential(account)
        this.medicalPendingCredential = pending
        this.medicalWriting = true
        this.medicalError = null
        this.invalidateMedical()
        try {
          const result = await changeCredential(session, account, pending, currentPassword, password)
          clearCredential(pending.actor)
          this.medicalPendingCredential = null
          window.dispatchEvent(new Event('medical-context-invalidated'))
          await router.replace('/access')
          return result
        } finally { this.medicalWriting = false }
      },
      clearMedicalCredential() {
        if (this.medicalWriting) throw new Error('密码操作正在执行')
        const pending = this.medicalPendingCredential
        if (pending === null) return
        if (this.medicalSession?.user.user_id !== pending.actor) fail('ACCESS_DENIED', '清除原密码请求需要重新核查本人会话')
        clearCredential(pending.actor)
        this.medicalPendingCredential = null
      },
      async changeMedicalProfile(account, fields) {
        if (this.medicalWriting || !this.medicalSession || account.user_id !== this.medicalSession.user.user_id) {
          fail('ACCESS_DENIED', '本人资料编辑没有取得有效会话')
        }
        const session = this.medicalSession
        this.medicalWriting = true
        this.medicalError = null
        try {
          const pending = await beginOwnProfile(account, fields)
          this.medicalPendingProfile = pending
          const result = await changeOwnProfile(session, account, fields, pending)
          clearOwnProfile(pending.actor)
          this.medicalPendingProfile = null
          await this.readMedical()
          window.dispatchEvent(new Event('medical-context-invalidated'))
          return result
        } finally { this.medicalWriting = false }
      },
      clearMedicalProfile() {
        if (this.medicalWriting) throw new Error('资料操作正在执行')
        const pending = this.medicalPendingProfile
        if (pending === null) return
        if (this.medicalSession?.user.user_id !== pending.actor) fail('ACCESS_DENIED', '清除原资料请求需要核查当前本人会话')
        clearOwnProfile(pending.actor)
        this.medicalPendingProfile = null
      },
      // 登录
      login(userInfo) {
        const username = userInfo.username.trim()
        const password = userInfo.password
        const code = userInfo.code
        const uuid = userInfo.uuid
        // 原登录入口承载本地Cookie模式，账户认证仍由已有服务执行。
        if (this.medicalMode || import.meta.env.VITE_APP_ENV === 'medical') return this.loginMedical(username, password)
        return new Promise((resolve, reject) => {
          login(username, password, code, uuid).then(res => {
            setToken(res.token)
            this.token = res.token
            useLockStore().unlockScreen()
            resolve()
          }).catch(error => {
            reject(error)
          })
        })
      },
      // 获取用户信息
      getInfo() {
        return new Promise((resolve, reject) => {
          getInfo().then(res => {
            const user = res.user
            let avatar = user.avatar || ""
            if (!isHttp(avatar)) {
              avatar = (isEmpty(avatar)) ? defAva : import.meta.env.VITE_APP_BASE_API + avatar
            }
            if (res.roles && res.roles.length > 0) { // 验证返回的roles是否是一个非空数组
              this.roles = res.roles
              this.permissions = res.permissions
            } else {
              this.roles = ['ROLE_DEFAULT']
            }
            this.id = user.userId
            this.name = user.userName
            this.nickName = user.nickName
            this.avatar = avatar
            cache.session.set('pwrChrtype', res.pwdChrtype)
            /* 初始密码提示 */
            if(res.isDefaultModifyPwd) {
              ElMessageBox.confirm('您的密码还是初始密码，请修改密码！',  '安全提示', {  confirmButtonText: '确定',  cancelButtonText: '取消',  type: 'warning' }).then(() => {
                router.push({ name: 'Profile', params: { activeTab: 'resetPwd' } })
              }).catch(() => {})
            }
            /* 过期密码提示 */
            if(!res.isDefaultModifyPwd && res.isPasswordExpired) {
              ElMessageBox.confirm('您的密码已过期，请尽快修改密码！',  '安全提示', {  confirmButtonText: '确定',  cancelButtonText: '取消',  type: 'warning' }).then(() => {
                router.push({ name: 'Profile', params: { activeTab: 'resetPwd' } })
              }).catch(() => {})
            }
            resolve(res)
          }).catch(error => {
            reject(error)
          })
        })
      },
      // 退出系统
      logOut() {
        return new Promise((resolve, reject) => {
          logout(this.token).then(() => {
            this.token = ''
            this.roles = []
            this.permissions = []
            removeToken()
            resolve()
          }).catch(error => {
            reject(error)
          })
        })
      }
    }
  })

export default useUserStore
