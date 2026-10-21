"""Author exact native action descriptors and typed parameter/response contracts. Does not execute endpoints."""
from pathlib import Path
import json,re,copy,hashlib
root=Path(__file__).resolve().parents[2]
p=root/'business/medical-persistence/src/main/resources/framework-binding-native.schema.json';s=json.loads(p.read_text());D=s['$defs'];ref=lambda n:{'$ref':'#/$defs/'+n}
def obj(props,required=()):return {'type':'object','additionalProperties':False,'properties':props,'required':list(required)}
def nullable(v):return {'anyOf':[v,{'type':'null'}]}
def typ(t):
 if t=='String':return nullable({'type':'string','maxLength':1048576})
 if t in ('Long','Integer'):return nullable(ref(t))
 if t=='boolean':return {'type':'boolean'}
 if t=='Date':return nullable({'type':'string','maxLength':32})
 if t.endswith('[]'):return nullable({'type':'array','items':ref(t[:-2])})
 if t.startswith(('List<','Set<')):
  k=t[t.index('<')+1:-1];return nullable({'type':'array','items':{'type':'string'} if k=='String' else ref('Read_'+k)})
 return nullable(ref('Read_'+t))
# Read contracts are independent from mutable projections; include original computed bean getters, never password.
for name in ['SysUser','SysRole','SysMenu','SysDept','SysDictType','SysDictData','SysPost','SysConfig','SysNotice','SysOperLog','SysLogininfor','GenTable','GenTableColumn']:
 source=list((root/'business').rglob(name+'.java'))[0];text=source.read_text();props={k:nullable({'type':'string'}) for k in ['createBy','createTime','updateBy','updateTime','remark']}
 props['params']=obj({})
 for t,field in re.findall(r'public\s+([\w<>\[\]]+)\s+get(\w+)\(\)',text):
  field=field[0].lower()+field[1:]
  if field=='password':continue
  props[field]=typ(t)
 for field in re.findall(r'public\s+boolean\s+is(\w+)\(\)',text):props[field[0].lower()+field[1:]]={'type':'boolean'}
 # Notice explicitly names its wire boolean isRead.
 if name=='SysNotice':props['isRead']={'type':'boolean'}
 D['Read_'+name]=obj(props)
 for kind in ['Single','Array','Table']:
  payload=nullable(ref('Read_'+name)) if kind=='Single' else {'type':'array','items':ref('Read_'+name)}
  fields={'code':{'type':'integer'},'msg':{'type':'string'}}
  fields['rows' if kind=='Table' else 'data']=payload
  if kind=='Table':fields['total']={'type':'integer','minimum':0}
  D[kind+'_'+name]=obj(fields,['code','msg'])
D['TreeNode']=obj({'id':ref('Long'),'label':{'type':'string'},'children':{'type':'array','items':ref('TreeNode')}},['id','label'])
D['TreeResponse']=obj({'code':{'type':'integer'},'msg':{'type':'string'},'data':{'type':'array','items':ref('TreeNode')}},['code','msg'])
D['MenuTreeResponse']=obj({'code':{'type':'integer'},'msg':{'type':'string'},'checkedKeys':{'type':'array','items':ref('Long')},'menus':{'type':'array','items':ref('TreeNode')}},['code','msg'])
D['DeptTreeResponse']=obj({'code':{'type':'integer'},'msg':{'type':'string'},'checkedKeys':{'type':'array','items':ref('Long')},'depts':{'type':'array','items':ref('TreeNode')}},['code','msg'])
D['UserInfoResponse']=copy.deepcopy(D['Single_SysUser']);D['UserInfoResponse']['properties'].update({k:{'type':'array','items':ref(v)} for k,v in [('posts','Read_SysPost'),('roles','Read_SysRole'),('postIds','Long'),('roleIds','Long')]})
D['UserRolesResponse']=obj({'code':{'type':'integer'},'msg':{'type':'string'},'user':ref('Read_SysUser'),'roles':{'type':'array','items':ref('Read_SysRole')}},['code','msg'])
D['NoticeTopResponse']=copy.deepcopy(D['Array_SysNotice']);D['NoticeTopResponse']['properties']['unreadCount']={'type':'integer','minimum':0}
D['NoticeReadUsersResponse']=obj({'code':{'type':'integer'},'msg':{'type':'string'},'total':{'type':'integer'},'rows':{'type':'array','items':obj({'userId':ref('Long'),'userName':nullable({'type':'string'}),'nickName':nullable({'type':'string'}),'deptName':nullable({'type':'string'}),'phonenumber':nullable({'type':'string'}),'readTime':nullable({'type':'string'})})}},['code','msg'])
D['StringResponse']=obj({'code':{'type':'integer'},'msg':{'type':'string'},'data':nullable({'type':'string'})},['code','msg'])
D['GeneratorInfoResponse']=obj({'code':{'type':'integer'},'msg':{'type':'string'},'data':obj({'info':ref('Read_GenTable'),'rows':{'type':'array','items':ref('Read_GenTableColumn')},'tables':{'type':'array','items':ref('Read_GenTable')}})},['code','msg'])
D['GeneratorPreviewResponse']=obj({'code':{'type':'integer'},'msg':{'type':'string'},'data':{'type':'object','maxProperties':1000,'propertyNames':{'pattern':'^[A-Za-z0-9_./-]{1,256}$'},'additionalProperties':{'type':'string','maxLength':1048576}}},['code','msg'])
QSTR={'type':'string','maxLength':512};QID={'type':'string','pattern':'^[0-9]{1,19}$'};QIDS={'type':'string','pattern':'^[0-9]{1,19}(,[0-9]{1,19})*$','maxLength':20000}
paging={'pageNum':{'type':'string','pattern':'^[1-9][0-9]{0,8}$'},'pageSize':{'type':'string','pattern':'^[1-9][0-9]{0,3}$'},'orderByColumn':{'type':'string','pattern':'^[a-zA-Z][a-zA-Z0-9_]{0,63}$'},'isAsc':{'enum':['asc','desc','ascending','descending']},'reasonable':{'enum':['true','false']}}
dates={key:{'type':'string','maxLength':32,'pattern':'^[0-9 :T-]+$'} for key in ['params[beginTime]','params[endTime]']}
filters={
'SysUser':'userId userName status phonenumber deptId', 'SysRole':'roleId roleName status roleKey', 'SysMenu':'menuName visible status', 'SysDept':'deptId parentId deptName status', 'SysPost':'postCode postName status', 'SysDictType':'dictName dictType status', 'SysDictData':'dictType dictLabel status', 'SysConfig':'configName configType configKey', 'SysNotice':'noticeTitle noticeType createBy', 'SysOperLog':'operIp title businessType businessTypes status operName','SysLogininfor':'ipaddr status userName','GenTable':'tableName tableComment'}
for name,fields in filters.items():
 q={field:copy.deepcopy(QID if field.endswith('Id') else QIDS if field=='businessTypes' else QSTR) for field in fields.split()};q.update(paging)
 if name in ['SysUser','SysRole','SysDictType','SysConfig','SysOperLog','SysLogininfor','GenTable']:q.update(dates)
 D['Query_'+name]=obj(q)
D['Query_None']=obj({})
rows=[];params={};baseRef='urn:medical:framework-native:1.0.0#/$defs/'
def add(module,ctrl,handler,method,path,permission,query=None,request=None,response=None,effect=None,identity=None):
 op='framework.'+ctrl+'.'+handler
 q=query or 'Query_None';params[op]=q
 if request is None:request=s['x-operation-bodies'].get(op,q)
 if response is None:response='MutationResponse' if method!='GET' else 'StringResponse'
 if effect is None:effect='NONE' if method=='GET' else 'WRITE'
 rows.append(dict(module_id=module,operation_id=identity or op,method=method,path=path,request_schema_ref=baseRef+request,response_schema_ref=baseRef+response,side_effect=effect,permission_code=permission,environment='DEVELOPMENT' if module=='CODE_GENERATOR' else 'ANY'))
modules=[('USER','SysUser','user','userId','userIds'),('ROLE','SysRole','role','roleId','roleIds'),('MENU','SysMenu','menu','menuId','menuId'),('DEPARTMENT','SysDept','dept','deptId','deptId'),('POST','SysPost','post','postId','postIds'),('DICTIONARY','SysDictType','dict/type','dictId','dictIds'),('DICTIONARY','SysDictData','dict/data','dictCode','dictCodes'),('PARAMETER','SysConfig','config','configId','configIds'),('NOTICE','SysNotice','notice','noticeId','noticeIds')]
for module,dto,path,key,keys in modules:
 ctrl=dto+'Controller';base='/api/system/'+path;perm='system:'+path.split('/')[0]+':'
 add(module,ctrl,'list','GET',base+'/list',perm+'list',query='Query_'+dto,response=('Array_' if dto in ('SysMenu','SysDept') else 'Table_')+dto)
 add(module,ctrl,'getInfo','GET',base+'/{'+key+'}',perm+'query',response='UserInfoResponse' if dto=='SysUser' else 'Single_'+dto)
 for handler,method,action in [('add','POST','add'),('edit','PUT','edit')]:add(module,ctrl,handler,method,base,perm+action)
 add(module,ctrl,'remove','DELETE',base+'/{'+keys+'}',perm+'remove')
 if dto not in ('SysMenu','SysDept','SysNotice'):add(module,ctrl,'export','POST',base+'/export',perm+'export',query='Query_'+dto,response='BinaryExportResponse',effect='EXPORT')
# exact auxiliary/read/write routes
for ctrl,module,base,perm in [('SysUserController','USER','/api/system/user','system:user'),('SysRoleController','ROLE','/api/system/role','system:role')]:
 add(module,ctrl,'changeStatus','PUT',base+'/changeStatus',perm+':edit')
add('USER','SysUserController','resetPwd','PUT','/api/system/user/resetPwd','system:user:resetPwd')
D['Query_UserAuthRole']=obj({'userId':QID,'roleIds':QIDS},['userId','roleIds']);add('USER','SysUserController','insertAuthRole','PUT','/api/system/user/authRole','system:user:edit',query='Query_UserAuthRole')
D['Query_UserImport']=obj({'updateSupport':{'enum':['true','false']}},['updateSupport']);D['Multipart_UserImport']=obj({'file':{'type':'string','contentMediaType':'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet','contentEncoding':'binary'},'updateSupport':{'type':'boolean'}},['file','updateSupport']);D['Multipart_UserImport']['x-http-transport']='multipart/form-data'
add('USER','SysUserController','importData','POST','/api/system/user/importData','system:user:import',query='Query_UserImport',request='Multipart_UserImport')
add('USER','SysUserController','importTemplate','POST','/api/system/user/importTemplate','system:user:import',response='BinaryExportResponse',effect='EXPORT')
add('USER','SysUserController','getInfo','GET','/api/system/user/','system:user:query',response='UserInfoResponse',identity='framework.SysUserController.getInfo.empty')
add('USER','SysUserController','authRole','GET','/api/system/user/authRole/{userId}','system:user:query',response='UserRolesResponse')
add('USER','SysUserController','deptTree','GET','/api/system/user/deptTree','system:user:list',query='Query_SysDept',response='TreeResponse')
add('ROLE','SysRoleController','dataScope','PUT','/api/system/role/dataScope','system:role:edit')
add('ROLE','SysRoleController','cancelAuthUser','PUT','/api/system/role/authUser/cancel','system:role:edit')
D['Query_RoleAuthUsers']=obj({'roleId':QID,'userIds':QIDS},['roleId','userIds'])
for handler,path in [('cancelAuthUserAll','cancelAll'),('selectAuthUserAll','selectAll')]:add('ROLE','SysRoleController',handler,'PUT','/api/system/role/authUser/'+path,'system:role:edit',query='Query_RoleAuthUsers')
D['Query_AllocatedUser']=obj(dict(paging,roleId=QID,userName=QSTR,phonenumber=QSTR))
for handler in ['allocatedList','unallocatedList']:add('ROLE','SysRoleController',handler,'GET','/api/system/role/authUser/'+handler,'system:role:list',query='Query_AllocatedUser',response='Table_SysUser')
add('ROLE','SysRoleController','deptTree','GET','/api/system/role/deptTree/{roleId}','system:role:query',response='DeptTreeResponse')
for module,ctrl,base,perm,dto in [('ROLE','SysRoleController','role','role','SysRole'),('POST','SysPostController','post','post','SysPost'),('DICTIONARY','SysDictTypeController','dict/type','dict','SysDictType')]:add(module,ctrl,'optionselect','GET','/api/system/'+base+'/optionselect','system:'+perm+':query',response='Array_'+dto)
for module,ctrl,base,perm in [('MENU','SysMenuController','menu','menu'),('DEPARTMENT','SysDeptController','dept','dept')]:add(module,ctrl,'updateSort','PUT','/api/system/'+base+'/updateSort','system:'+perm+':edit')
add('MENU','SysMenuController','treeselect','GET','/api/system/menu/treeselect','system:menu:query',query='Query_SysMenu',response='TreeResponse')
add('MENU','SysMenuController','roleMenuTreeselect','GET','/api/system/menu/roleMenuTreeselect/{roleId}','system:role:query',response='MenuTreeResponse')
add('DEPARTMENT','SysDeptController','excludeChild','GET','/api/system/dept/list/exclude/{deptId}','system:dept:list',response='Array_SysDept')
add('DICTIONARY','SysDictDataController','dictType','GET','/api/system/dict/data/type/{dictType}','system:dict:query',response='Array_SysDictData')
for module,ctrl,path,perm in [('DICTIONARY','SysDictTypeController','dict/type','dict'),('PARAMETER','SysConfigController','config','config')]:add(module,ctrl,'refreshCache','DELETE','/api/system/'+path+'/refreshCache','system:'+perm+':remove')
add('PARAMETER','SysConfigController','getConfigKey','GET','/api/system/config/configKey/{configKey}','system:config:query',response='StringResponse')
add('NOTICE','SysNoticeController','listTop','GET','/api/system/notice/listTop','system:notice:list',response='NoticeTopResponse')
D['Query_NoticeMark']=obj({'noticeId':QID},['noticeId']);D['Query_NoticeBatch']=obj({'ids':QIDS},['ids']);D['Query_NoticeUsers']=obj(dict(paging,noticeId=QID,searchValue=QSTR),['noticeId'])
for handler,query in [('markRead','Query_NoticeMark'),('markReadAll','Query_NoticeBatch')]:add('NOTICE','SysNoticeController',handler,'POST','/api/system/notice/'+handler,'system:notice:list',query=query)
add('NOTICE','SysNoticeController','readUsersList','GET','/api/system/notice/readUsers/list','system:notice:list',query='Query_NoticeUsers',response='NoticeReadUsersResponse')
for module,ctrl,dto,path,ids in [('OPERATION_LOG','SysOperlogController','SysOperLog','operlog','operIds'),('LOGIN_LOG','SysLogininforController','SysLogininfor','logininfor','infoIds')]:
 base='/api/monitor/'+path;perm='monitor:'+path+':'
 add(module,ctrl,'list','GET',base+'/list',perm+'list',query='Query_'+dto,response='Table_'+dto)
 add(module,ctrl,'export','POST',base+'/export',perm+'export',query='Query_'+dto,response='BinaryExportResponse',effect='EXPORT')
 add(module,ctrl,'remove','DELETE',base+'/{'+ids+'}',perm+'remove')
 add(module,ctrl,'clean','DELETE',base+'/clean',perm+'remove')
add('LOGIN_LOG','SysLogininforController','unlock','GET','/api/monitor/logininfor/unlock/{userName}','monitor:logininfor:unlock',effect='WRITE',response='MutationResponse')
for handler,path in [('genList','list'),('dataList','db/list')]:add('CODE_GENERATOR','GenController',handler,'GET','/api/tool/gen/'+path,'tool:gen:list',query='Query_GenTable',response='Table_GenTable')
add('CODE_GENERATOR','GenController','getInfo','GET','/api/tool/gen/{tableId}','tool:gen:query',response='GeneratorInfoResponse')
add('CODE_GENERATOR','GenController','editSave','PUT','/api/tool/gen','tool:gen:edit')
add('CODE_GENERATOR','GenController','remove','DELETE','/api/tool/gen/{tableIds}','tool:gen:remove')
D['Query_GeneratorImport']=obj({'tables':{'type':'string','maxLength':20000,'pattern':'^[A-Za-z_][A-Za-z0-9_]*(,[A-Za-z_][A-Za-z0-9_]*)*$'},'tplWebType':{'enum':['element-ui','element-plus','element-plus-typescript']}},['tables','tplWebType'])
add('CODE_GENERATOR','GenController','importTableSave','POST','/api/tool/gen/importTable','tool:gen:import',query='Query_GeneratorImport')
add('CODE_GENERATOR','GenController','synchDb','GET','/api/tool/gen/synchDb/{tableName}','tool:gen:edit',effect='WRITE',response='MutationResponse')
add('CODE_GENERATOR','GenController','preview','GET','/api/tool/gen/preview/{tableId}','tool:gen:preview',response='GeneratorPreviewResponse')
add('CODE_GENERATOR','GenController','download','GET','/api/tool/gen/download/{tableName}','tool:gen:code',response='BinaryExportResponse',effect='EXPORT')
D['Query_GeneratorBatch']=obj({'tables':D['Query_GeneratorImport']['properties']['tables']},['tables']);add('CODE_GENERATOR','GenController','batchGenCode','GET','/api/tool/gen/batchGenCode','tool:gen:code',query='Query_GeneratorBatch',response='BinaryExportResponse',effect='EXPORT')
add('CODE_GENERATOR','GenController','columnList','GET','/api/tool/gen/column/{tableId}','tool:gen:list',response='Table_GenTableColumn')
D['Query_SysOperLog']['patternProperties']={'^businessTypes\\[[0-9]{1,3}\\]$': {'type': 'string', 'pattern': '^[0-9]{1,3}$'}}
D['Query_FrameworkJobExport']={'type': 'object', 'additionalProperties': False, 'properties': {'jobName': {'type': 'string', 'maxLength': 128}, 'jobGroup': {'type': 'string', 'maxLength': 128}, 'status': {'type': 'string', 'maxLength': 128}, 'params[beginTime]': {'type': 'string', 'maxLength': 128}, 'params[endTime]': {'type': 'string', 'maxLength': 128}, 'pageNum': {'type': 'string', 'maxLength': 128}, 'pageSize': {'type': 'string', 'maxLength': 128}}}
s['x-operation-parameters']=params
s['x-excluded-operations']={'framework.GenController.createTableSave':'Runtime explicitly denies arbitrary SQL creation.','framework.GenController.genCode':'Runtime explicitly denies filesystem code generation.'}
p.write_text(json.dumps(s,ensure_ascii=False,indent=2)+'\n')
(root/'business/medical-persistence/src/main/resources/framework-binding-native-actions.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n')
