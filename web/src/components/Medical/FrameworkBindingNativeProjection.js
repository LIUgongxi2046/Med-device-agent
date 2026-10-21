// Matches the additive closed server body contracts. This is UX projection, never an authorization check.
const fields = {
  "POST /api/system/user": [
    "remark",
    "deptId",
    "userName",
    "nickName",
    "email",
    "phonenumber",
    "sex",
    "avatar",
    "password",
    "status",
    "roleIds",
    "postIds"
  ],
  "PUT /api/system/user": [
    "remark",
    "userId",
    "deptId",
    "userName",
    "nickName",
    "email",
    "phonenumber",
    "sex",
    "avatar",
    "password",
    "status",
    "roleIds",
    "postIds"
  ],
  "POST /api/system/role": [
    "remark",
    "roleName",
    "roleKey",
    "roleSort",
    "dataScope",
    "menuCheckStrictly",
    "deptCheckStrictly",
    "status",
    "menuIds",
    "deptIds"
  ],
  "PUT /api/system/role": [
    "remark",
    "roleId",
    "roleName",
    "roleKey",
    "roleSort",
    "dataScope",
    "menuCheckStrictly",
    "deptCheckStrictly",
    "status",
    "menuIds",
    "deptIds"
  ],
  "POST /api/system/menu": [
    "remark",
    "menuName",
    "parentId",
    "orderNum",
    "path",
    "component",
    "query",
    "routeName",
    "isFrame",
    "isCache",
    "menuType",
    "visible",
    "status",
    "perms",
    "icon"
  ],
  "PUT /api/system/menu": [
    "remark",
    "menuId",
    "menuName",
    "parentId",
    "orderNum",
    "path",
    "component",
    "query",
    "routeName",
    "isFrame",
    "isCache",
    "menuType",
    "visible",
    "status",
    "perms",
    "icon"
  ],
  "POST /api/system/dept": [
    "remark",
    "parentId",
    "deptName",
    "orderNum",
    "leader",
    "phone",
    "email",
    "status"
  ],
  "PUT /api/system/dept": [
    "remark",
    "deptId",
    "parentId",
    "deptName",
    "orderNum",
    "leader",
    "phone",
    "email",
    "status"
  ],
  "POST /api/system/dict/type": [
    "remark",
    "dictName",
    "dictType",
    "status"
  ],
  "PUT /api/system/dict/type": [
    "remark",
    "dictId",
    "dictName",
    "dictType",
    "status"
  ],
  "POST /api/system/dict/data": [
    "remark",
    "dictSort",
    "dictLabel",
    "dictValue",
    "dictType",
    "cssClass",
    "listClass",
    "isDefault",
    "status"
  ],
  "PUT /api/system/dict/data": [
    "remark",
    "dictCode",
    "dictSort",
    "dictLabel",
    "dictValue",
    "dictType",
    "cssClass",
    "listClass",
    "isDefault",
    "status"
  ],
  "POST /api/system/post": [
    "remark",
    "postCode",
    "postName",
    "postSort",
    "status"
  ],
  "PUT /api/system/post": [
    "remark",
    "postId",
    "postCode",
    "postName",
    "postSort",
    "status"
  ],
  "POST /api/system/config": [
    "remark",
    "configName",
    "configKey",
    "configValue",
    "configType"
  ],
  "PUT /api/system/config": [
    "remark",
    "configId",
    "configName",
    "configKey",
    "configValue",
    "configType"
  ],
  "POST /api/system/notice": [
    "remark",
    "noticeTitle",
    "noticeType",
    "noticeContent",
    "status"
  ],
  "PUT /api/system/notice": [
    "remark",
    "noticeId",
    "noticeTitle",
    "noticeType",
    "noticeContent",
    "status"
  ],
  "PUT /api/system/user/changeStatus": [
    "userId",
    "status"
  ],
  "PUT /api/system/role/changeStatus": [
    "roleId",
    "status"
  ],
  "PUT /api/system/user/resetPwd": [
    "userId",
    "password"
  ],
  "PUT /api/system/role/dataScope": [
    "roleId",
    "dataScope",
    "deptCheckStrictly",
    "deptIds"
  ],
  "PUT /api/system/role/authUser/cancel": [
    "userId",
    "roleId"
  ],
  "PUT /api/system/menu/updateSort": [
    "menuIds",
    "orderNums"
  ],
  "PUT /api/system/dept/updateSort": [
    "deptIds",
    "orderNums"
  ],
  "PUT /api/tool/gen": [
    "remark",
    "params",
    "tableId",
    "tableName",
    "tableComment",
    "subTableName",
    "subTableFkName",
    "className",
    "tplCategory",
    "tplWebType",
    "packageName",
    "moduleName",
    "businessName",
    "functionName",
    "functionAuthor",
    "formColNum",
    "genType",
    "genPath",
    "columns"
  ]
}
const columnFields = ["columnId", "tableId", "columnName", "columnComment", "columnType", "javaType", "javaField", "isPk", "isIncrement", "isRequired", "isInsert", "isEdit", "isList", "isQuery", "queryType", "htmlType", "dictType", "sort"]
const generatorParams = ['treeCode', 'treeParentCode', 'treeName', 'parentMenuId']
const pick = (value, keys) => Object.fromEntries(keys.filter(key => Object.prototype.hasOwnProperty.call(value, key) && value[key] !== undefined).map(key => [key, value[key]]))
export function projectFrameworkNativeWrite(method, path, data) {
  const verb = String(method || 'GET').toUpperCase()
  const normalized = String(path || '').split('?')[0].replace(/\/$/, '')
  const keys = fields[`${verb} ${normalized}`]
  if (!keys || !data || typeof data !== 'object' || Array.isArray(data)) return data
  const projected = pick(data, keys)
  if (verb === 'PUT' && normalized === '/api/system/user') delete projected.password
  if (verb === 'PUT' && normalized === '/api/tool/gen') {
    if (Array.isArray(projected.columns)) projected.columns = projected.columns.map(column => pick(column, columnFields))
    if (projected.params && typeof projected.params === 'object') projected.params = pick(projected.params, generatorParams)
    // Neither custom filesystem mode nor destination is part of the editor's medical write capability.
    delete projected.genPath
    delete projected.genType
  }
  return projected
}
