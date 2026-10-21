"""Author additive, closed native DTO contracts from this checked source tree. Not a test runner."""
from pathlib import Path
import re,json,copy,hashlib
root=Path(__file__).resolve().parents[2]
classes={}
for name in ['SysUser','SysRole','SysMenu','SysDept','SysDictType','SysDictData','SysPost','SysConfig','SysNotice','SysUserRole','GenTable','GenTableColumn']:
    matches=list((root/'business').rglob(name+'.java'))
    classes[name]=matches[0]
D={}
nullable=lambda s:{'anyOf':[s,{'type':'null'}]}
ref=lambda n:{'$ref':'#/$defs/'+n}
def obj(p,required=()):return {'type':'object','additionalProperties':False,'properties':p,'required':list(required)}
D['Long']={'anyOf':[{'type':'integer','minimum':0,'maximum':9223372036854775807},{'type':'string','pattern':'^(0|[1-9][0-9]{0,18})$'}]}
D['Integer']={'anyOf':[{'type':'integer','minimum':-2147483648,'maximum':2147483647},{'type':'string','pattern':'^-?[0-9]{1,10}$'}]}
D['EmptyParams']=obj({})
def dtype(t):
    if t=='String':return nullable({'type':'string','maxLength':65536})
    if t in ('Long','Integer'):return nullable(ref(t))
    if t=='boolean':return {'type':'boolean'}
    if t=='Date':return nullable({'type':'string','maxLength':32})
    if t.endswith('[]'):return nullable({'type':'array','maxItems':1000,'items':ref(t[:-2])})
    if t.startswith('List<') or t.startswith('Set<'):
        inside=t[t.index('<')+1:-1];item={'type':'string','maxLength':512} if inside=='String' else ref(inside)
        return nullable({'type':'array','maxItems':1000,'items':item})
    if t in classes:return nullable(ref(t))
    raise ValueError(t)
base={k:nullable({'type':'string','maxLength':n}) for k,n in [('createBy',128),('createTime',32),('updateBy',128),('updateTime',32),('remark',65536)]}
base['params']=nullable(ref('EmptyParams'))
required={}
for name,path in classes.items():
    source=path.read_text();props=copy.deepcopy(base) if 'extends BaseEntity' in source else {}
    required[name]=[]
    for t,field in re.findall(r'private\s+([\w<>\[\]]+)\s+(\w+)\s*(?:[;=])',source):
        props[field]=dtype(t)
    # Validator annotations occur on getters for system DTOs and on fields for generator DTOs.
    for field,value in list(props.items()):
        getter=re.search(r'((?:\s*@[^\n]+\n)+)\s*public\s+\w+\s+get'+field[0].upper()+field[1:]+r'\(',source)
        declaration=re.search(r'((?:\s*@[^\n]+\n)+)\s*private\s+[\w<>\[\]]+\s+'+field+r'\s*;',source)
        anns=(getter or declaration).group(1) if getter or declaration else ''
        size=re.search(r'@Size\([^)]*max\s*=\s*(\d+)',anns)
        primitive=value.get('anyOf',[value])[0]
        if size and primitive.get('type')=='string':primitive['maxLength']=int(size.group(1))
        if '@NotBlank' in anns:
            primitive['minLength']=1;primitive['pattern']='\\S';props[field]=primitive;required[name].append(field)
        elif '@NotNull' in anns:props[field]=primitive;required[name].append(field)
        if '@Xss' in anns:primitive['x-native-validator']='com.ruoyi.common.xss.Xss'
    if name in ('SysUser','SysRole'):props['admin']={'type':'boolean','description':'Native read-only computed projection; ignored on binding.'}
    D[name]=obj(props)
    D[name]['x-native-source']=str(path.relative_to(root));D[name]['x-native-source-sha256']=hashlib.sha256(path.read_bytes()).hexdigest()
# Native annotation has an exact dictionary-name pattern.
D['SysDictType']['properties']['dictType']={'type':'string','minLength':1,'maxLength':100,'pattern':'^[a-z][a-z0-9_]*$'}
D['GeneratorParams']=obj({k:nullable({'type':'string','maxLength':200}) for k in ['treeCode','treeParentCode','treeName']})
D['GeneratorParams']['properties']['parentMenuId']=nullable(ref('Long'))
D['GenTable']['properties']['params']=ref('GeneratorParams')
# No filesystem destinations can be submitted to the metadata update API.
D['GenTable']['properties']['genType']={'enum':['0',None]}
D['GenTable']['properties']['genPath']={'enum':['/',None,'']}
D['GenTableColumn']['required']=required['GenTableColumn']
D['MutationResponse']=obj({'code':{'type':'integer'},'msg':{'type':'string'},'data':{'type':'integer'}},['code','msg'])
D['BinaryExportResponse']={'type':'string','contentEncoding':'base64','contentMediaType':'application/octet-stream','description':'Actual binary HTTP response, not a JSON envelope.'}
D['NoBody']={'type':'null','description':'No JSON body. Named query/path parameters remain declared separately.'}
operations={}
for name in ['SysUser','SysRole','SysMenu','SysDept','SysDictType','SysDictData','SysPost','SysConfig','SysNotice']:
    controller=name+'Controller'
    for handler in ['add','edit']:
        key='Body_'+controller+'_'+handler;definition=copy.deepcopy(D[name]);definition['required']=list(required[name])
        if handler=='edit':
            identity={'SysUser':'userId','SysRole':'roleId','SysMenu':'menuId','SysDept':'deptId','SysDictType':'dictId','SysDictData':'dictCode','SysPost':'postId','SysConfig':'configId','SysNotice':'noticeId'}[name]
            definition['required'].append(identity);definition['properties'][identity]=ref('Long')
        if name=='SysUser':
            if handler=='edit':definition['properties']['password']={'enum':['',None],'description':'Compatibility blank only; nonempty password edit is forbidden. Use resetPwd.'}
            else:definition['properties']['password']={'type':'string','minLength':1,'maxLength':72,'writeOnly':True};definition['required'].append('password')
        D[key]=definition;operations['framework.'+controller+'.'+handler]=key
for controller,idname in [('SysUserController','userId'),('SysRoleController','roleId')]:
    key='Body_'+controller+'_changeStatus';D[key]=obj({idname:ref('Long'),'status':{'enum':['0','1']}},[idname,'status']);operations['framework.'+controller+'.changeStatus']=key
D['Body_SysUserController_resetPwd']=obj({'userId':ref('Long'),'password':{'type':'string','minLength':1,'maxLength':72,'writeOnly':True}},['userId','password']);operations['framework.SysUserController.resetPwd']='Body_SysUserController_resetPwd'
D['Body_SysRoleController_dataScope']=obj({'roleId':ref('Long'),'dataScope':{'enum':['1','2','3','4','5']},'deptCheckStrictly':{'type':'boolean'},'deptIds':{'type':'array','maxItems':1000,'items':ref('Long')}},['roleId','dataScope','deptIds']);operations['framework.SysRoleController.dataScope']='Body_SysRoleController_dataScope'
D['Body_SysRoleController_cancelAuthUser']=obj({'userId':ref('Long'),'roleId':ref('Long')},['userId','roleId']);operations['framework.SysRoleController.cancelAuthUser']='Body_SysRoleController_cancelAuthUser'
for controller,ids in [('SysMenuController','menuIds'),('SysDeptController','deptIds')]:
    key='Body_'+controller+'_updateSort';D[key]=obj({ids:{'type':'string','maxLength':20000,'pattern':'^[0-9]+(,[0-9]+)*$'},'orderNums':{'type':'string','maxLength':20000,'pattern':'^[0-9]+(,[0-9]+)*$'}},[ids,'orderNums']);operations['framework.'+controller+'.updateSort']=key
key='Body_GenController_editSave';D[key]=copy.deepcopy(D['GenTable']);D[key]['required']=required['GenTable']+['tableId','columns'];D[key]['properties']['tableId']=ref('Long');operations['framework.GenController.editSave']=key
schema={'$schema':'https://json-schema.org/draft/2020-12/schema','$id':'urn:medical:framework-native:1.0.0','$defs':D,'x-operation-bodies':operations,'x-policy':'Closed approved native request subsets. Original controller validators, native permissions and data-scope guards remain mandatory. Additional safety limits are explicit, not a claim that native DTOs reject every extra field.'}
(root/'business/medical-persistence/src/main/resources/framework-binding-native.schema.json').write_text(json.dumps(schema,ensure_ascii=False,indent=2)+'\n')
# Closed mutable projections are intentionally separate from read DTOs. UI clients must submit these fields, not echo entities.
mutable={
'SysUser':'deptId userName nickName email phonenumber sex avatar status roleIds postIds remark',
'SysRole':'roleName roleKey roleSort dataScope menuCheckStrictly deptCheckStrictly status menuIds deptIds remark',
'SysMenu':'menuName parentId orderNum path component query routeName isFrame isCache menuType visible status perms icon remark',
'SysDept':'parentId deptName orderNum leader phone email status remark',
'SysPost':'postCode postName postSort status remark',
'SysDictType':'dictName dictType status remark',
'SysDictData':'dictSort dictLabel dictValue dictType cssClass listClass isDefault status remark',
'SysConfig':'configName configKey configValue configType remark',
'SysNotice':'noticeTitle noticeType noticeContent status remark',
'GenTable':'tableId tableName tableComment subTableName subTableFkName className tplCategory tplWebType packageName moduleName businessName functionName functionAuthor formColNum genType genPath columns params remark'
}
identities={'SysUser':'userId','SysRole':'roleId','SysMenu':'menuId','SysDept':'deptId','SysPost':'postId','SysDictType':'dictId','SysDictData':'dictCode','SysConfig':'configId','SysNotice':'noticeId'}
for op,key in operations.items():
    controller,handler=op.split('.')[1:]
    if handler not in ('add','edit','editSave'):continue
    dto=controller.removesuffix('Controller') if controller!='GenController' else 'GenTable'
    allowed=set(mutable[dto].split())
    if dto=='SysUser':allowed.add('password')
    if handler=='edit':allowed.add(identities[dto])
    D[key]['properties']={k:v for k,v in D[key]['properties'].items() if k in allowed}
    if dto=='SysRole':D[key]['required'].append('menuIds');D[key]['properties']['menuIds']={'type':'array','maxItems':1000,'items':ref('Long')}
    if dto=='SysDept':D[key]['required'].append('parentId');D[key]['properties']['parentId']=ref('Long')
    if dto=='SysUser':
        D[key]['required']+=['roleIds','postIds'];D[key]['properties']['roleIds']={'type':'array','maxItems':1000,'items':ref('Long')};D[key]['properties']['postIds']={'type':'array','maxItems':1000,'items':ref('Long')}
# No unspecified keys in nested generator metadata; ownership is also checked against original development-table rows at runtime.
column_mutable='columnId tableId columnName columnComment columnType javaType javaField isPk isIncrement isRequired isInsert isEdit isList isQuery queryType htmlType dictType sort'.split()
D['GenTableColumn']['properties']={k:v for k,v in D['GenTableColumn']['properties'].items() if k in column_mutable}
D['GenTableColumn']['required']=['columnId','tableId','javaField'];D['GenTableColumn']['properties']['columnId']=ref('Long');D['GenTableColumn']['properties']['tableId']=ref('Long')
D['Body_GenController_editSave']['properties']['columns']={'type':'array','minItems':1,'maxItems':1000,'items':ref('GenTableColumn')}
D['Body_GenController_editSave']['properties']['tplCategory']={'enum':['crud','tree','sub']}
D['Body_GenController_editSave']['properties']['tplWebType']={'enum':['element-ui','element-plus','element-plus-typescript']}
D['Body_GenController_editSave']['allOf']=[
 {'if':{'properties':{'tplCategory':{'const':'tree'}},'required':['tplCategory']},'then':{'required':['params'],'properties':{'params':{'required':['treeCode','treeParentCode','treeName'],'properties':{k:{'type':'string','minLength':1,'maxLength':200} for k in ['treeCode','treeParentCode','treeName']}}}}},
 {'if':{'properties':{'tplCategory':{'const':'sub'}},'required':['tplCategory']},'then':{'required':['subTableName','subTableFkName'],'properties':{k:{'type':'string','minLength':1,'maxLength':200} for k in ['subTableName','subTableFkName']}}}
]
(root/'business/medical-persistence/src/main/resources/framework-binding-native.schema.json').write_text(json.dumps(schema,ensure_ascii=False,indent=2)+'\n')
