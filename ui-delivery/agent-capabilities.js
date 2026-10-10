function createAgentCapabilities(ui) {
  const {escape,button,tag,table,empty,options,field}=WorkspaceUI;
  const {openDialog,closeProductDialog,dialogError,refresh,changed,models}=ui;
  const skills=[{id:'demo-skill-evidence',name:'证据范围核查（演示）',version:'1.2',purpose:'核对选定资料与引用位置',source:'设计演示能力定义',scope:'当前项目与资料集',action:'能力定义',status:'待审核',verified:false},{id:'demo-skill-fields',name:'技术字段比对（演示）',version:'2.0',purpose:'比对字段取值、单位及适用条件',source:'设计演示能力定义',scope:'产品技术要求与原始验证记录',action:'能力定义',status:'待审核',verified:false}],tools=[{id:'demo-tool-read',name:'项目资料读取（演示）',version:'1.0',purpose:'读取获授权资料版本',source:'设计演示工具定义',scope:'当前项目资料',action:'读取',status:'待验证',verified:false},{id:'demo-tool-export',name:'固定成果导出（演示）',version:'1.1',purpose:'输出指定版本文稿与来源',source:'设计演示工具定义',scope:'当前文稿与引用版本',action:'导出',status:'待验证',verified:false}];
  const definitions=[
    ['任务规划','目标、模板、资料清单与预算','章节计划、研究问题、依赖与完成条件','等待人工确认计划'],
    ['资料研究','本次资料范围与研究问题','原文位置、支持或冲突证据及缺项','仅检索本次授权范围'],
    ['技术字段核验','严格字段及指定权威资料','原始取值、单位、条件与比对问题','缺失字段等待补证'],
    ['章节撰写','已确认计划、证据与上游章节','章节候选正文、引用与未解决问题','人工采纳后更新正文'],
    ['来源与规则审查','候选正文、原始证据与规则','引用错误、字段冲突与跨章问题','保留独立审查职责'],
    ['总稿整理','已核验章节与人工意见','固定版本总稿、来源附录与成果清单','签发状态由授权人员决定']
  ];
  const limits=[['questions','研究问题上限'],['rounds','追加研究轮数'],['concurrency','并发数'],['revisions','修订轮数'],['calls','总调用次数'],['minutes','任务时限（分钟）'],['tokens','Token 上限'],['cost','费用上限']];
  function state(config) {
    if(!config.capabilities)config.capabilities={roles:definitions.map(([name,input,output,boundary])=>({name,input,output,boundary,model:'',skillIds:[],toolIds:[]})),limits:{},goal:'',inputs:'',outputs:'',documents:'',checkpoints:'',defaultCollection:'',revision:0};
    return config.capabilities;
  }
  function save(config,action){state(config).revision+=1;changed(config,action);closeProductDialog();refresh()}
  function render(config,tab) {
    const value=state(config),editable=config.status==='草稿';
    if(tab==='roles')return `<div class="section-intro"><div><h3>专业角色与交付职责</h3></div>${tag(`能力配置 v${value.revision}`,'info')}</div><div class="role-grid">${value.roles.map((role,index)=>`<article class="role-card"><div class="panel-top"><h3>${escape(role.name)}</h3>${tag(role.model?'已登记':'待配置','warn')}</div><dl><dt>接收</dt><dd>${escape(role.input)}</dd><dt>产出</dt><dd>${escape(role.output)}</dd><dt>职责限制</dt><dd>${escape(role.boundary)}</dd></dl><div class="role-resources">${role.skillIds.length} 项 Skill · ${role.toolIds.length} 项工具 · ${role.model?'模型已选择':'模型未选择'}</div>${button(editable?'配置职责与能力':'查看职责与能力','cap-role',index)}</article>`).join('')}</div>`;
    if(tab==='capabilities')return `<div class="section-intro"><div><h3>Skill 与工具目录</h3><p>分别维护来源、版本和审核状态，选入 Agent 后继续校验业务权限。</p></div><div class="actions">${button('登记 Skill','cap-new-skill','',true,!editable)}${button('登记工具','cap-new-tool','',false,!editable)}</div></div><h3>Skill</h3>${skills.length?table(['名称与版本','用途与来源','状态','操作'],skills.map(item=>`<tr><td>${escape(item.name)}<small>${escape(item.version)}</small></td><td>${escape(item.purpose)}<small>${escape(item.source)}</small></td><td>${tag(item.status,'warn')}</td><td>${button('查看能力详情','cap-skill',item.id)}</td></tr>`)):empty('尚未登记 Skill','从实际能力文件登记来源与版本，审核通过后才可发布。')}<h3>工具</h3>${tools.length?table(['工具与版本','动作与范围','连接','操作'],tools.map(item=>`<tr><td>${escape(item.name)}<small>${escape(item.version)}</small></td><td>${escape(item.action)} · ${escape(item.scope)}</td><td>${tag('未验证','warn')}</td><td>${button('查看授权','cap-tool',item.id)}</td></tr>`)):empty('尚未登记工具','登记资料读取、检索、检查或导出工具；外部写入需要独立授权。')}<div class="alert info">能力目录保存配置登记，执行与审核服务尚未接入。选择工具不会授予删除、核准或签发权限。</div>`;
    if(tab==='limits')return `<div class="section-intro"><div><h3>研究与修订上限</h3><p>全部专业角色共享任务上限；达到条件后停止新的派发。</p></div>${button('编辑上限','cap-limits','',true,!editable)}</div><div class="limit-grid">${limits.map(([id,label])=>`<div><span>${label}</span><strong>${value.limits[id]===undefined?'未配置':escape(value.limits[id])}</strong></div>`).join('')}</div><div class="alert info">发布前必须填写上限。实际用量、在途用量及待结算费用由运行服务返回。</div><h3>停止条件</h3><ul class="boundary-list"><li>没有新增有效证据：保留未解决问题，等待补证或调整计划。</li><li>达到研究、修订、调用、时间或费用上限：停止新增派发。</li><li>取消请求：进入取消中，等待在途调用终止确认。</li><li>继续处理：预览受影响章节，确认新的输入版本与预算。</li></ul>`;
    return '';
  }
  function summary(config) {const value=state(config);return `<section class="cap-summary"><div class="panel-top"><h3>业务输入与输出</h3>${button('编辑业务约定','cap-contract','',false,config.status!=='草稿')}</div><div class="api-facts">${[['业务目标',value.goal],['支持文档',value.documents],['必需输入',value.inputs],['输出清单',value.outputs],['人工检查点',value.checkpoints],['默认资料集',value.defaultCollection||'未选择；创建任务时确认范围']].map(([label,text])=>`<div><span>${label}</span><strong>${escape(text||'待配置')}</strong></div>`).join('')}</div></section>`}
  function check(config){const value=state(config);return [['业务输入与输出已填写',Boolean(value.goal&&value.inputs&&value.outputs&&value.documents&&value.checkpoints)],['研究与修订上限已填写',limits.every(([id])=>value.limits[id]!==undefined)],['角色模型及能力已核准',value.roles.every(role=>role.model&&models.usable(role.model)&&role.skillIds.length&&role.skillIds.every(id=>skills.find(x=>x.id===id)?.status==='已核准'))],['角色工具已验证',value.roles.every(role=>role.toolIds.length&&role.toolIds.every(id=>tools.find(x=>x.id===id)?.verified))]]}
  function handle(action,id,config) {
    if(!action.startsWith('cap-'))return false;
    const value=state(config),editable=config.status==='草稿';
    if(action==='cap-role') {
      const role=value.roles[Number(id)];
      openDialog(role.name,`<fieldset class="form cap-fieldset" ${editable?'':'disabled'}><label class="field">接收内容<textarea name="input" required>${escape(role.input)}</textarea></label><label class="field">产出要求<textarea name="output" required>${escape(role.output)}</textarea></label><label class="field">职责限制<textarea name="boundary" required>${escape(role.boundary)}</textarea></label><label class="field">模型连接<select name="model">${models.selectOptions(role.model)}</select></label><fieldset class="choice-list"><legend>Skill 版本</legend>${skills.length?skills.map(item=>`<label><input type="checkbox" name="skills" value="${item.id}" ${role.skillIds.includes(item.id)?'checked':''}>${escape(item.name)} ${escape(item.version)} · ${escape(item.status)}</label>`).join(''):'暂无已登记能力'}</fieldset><fieldset class="choice-list"><legend>工具版本</legend>${tools.length?tools.map(item=>`<label><input type="checkbox" name="tools" value="${item.id}" ${role.toolIds.includes(item.id)?'checked':''}>${escape(item.name)} ${escape(item.version)} · 未验证</label>`).join(''):'暂无已登记工具'}</fieldset></fieldset>`,editable?'保存职责':'完成查看',editable?values=>{Object.assign(role,{input:values.get('input').trim(),output:values.get('output').trim(),boundary:values.get('boundary').trim(),model:values.get('model'),skillIds:values.getAll('skills'),toolIds:values.getAll('tools')});save(config,'修改专业角色配置')}:null);return true;
    }
    if(action==='cap-skill'||action==='cap-tool') {
      const item=(action==='cap-skill'?skills:tools).find(x=>x.id===id);if(!item)throw new Error('能力记录不存在');
      openDialog(item.name,`<div class="api-facts">${Object.entries(item).filter(([key])=>!['id','verified'].includes(key)).map(([key,text])=>`<div><span>${escape({name:'名称',version:'版本',purpose:'用途',source:'来源',scope:'授权范围',action:'动作类型',status:'状态'}[key]||key)}</span><strong>${escape(text)}</strong></div>`).join('')}</div><div class="alert info">审核与连接验证服务尚未接入。此记录不能作为已核准能力发布。</div>`,'完成查看');return true;
    }
    if(!editable){dialogError('请创建草稿后编辑配置。');return true}
    if(action==='cap-new-skill'||action==='cap-new-tool') {
      const skill=action==='cap-new-skill',list=skill?skills:tools;
      openDialog(skill?'登记 Skill':'登记工具',`<div class="form two">${field('名称','name','','text','required')}${field('版本','version','','text','required')}<label class="field wide">用途<textarea name="purpose" required></textarea></label>${field('来源地址或文件路径','source','','text','required')}${skill?field('适用文档与所需工具','scope','','text','required'):`<label class="field">动作类型<select name="action">${options([['读取','读取'],['生成草稿','生成草稿'],['导出','导出'],['外部写入','外部写入']],'读取')}</select></label>${field('允许的数据类别与目标系统','scope','','text','required')}`}</div><p class="muted">提交后进入待审核或待验证，正式安装和执行需要管理服务。</p>`,'提交登记',values=>{const name=values.get('name').trim(),version=values.get('version').trim();if(!name||!version){dialogError('名称与版本必须填写。');return}if(list.some(item=>item.name===name&&item.version===version)){dialogError('此名称和版本已登记。');return}list.push({id:`${skill?'skill':'tool'}-${list.length+1}`,name,version,purpose:values.get('purpose').trim(),source:values.get('source').trim(),scope:values.get('scope').trim(),action:skill?'能力定义':values.get('action'),status:skill?'待审核':'待验证',verified:false});save(config,'登记能力依赖')});return true;
    }
    if(action==='cap-limits') {
      openDialog('研究与修订上限',`<div class="form two">${limits.map(([key,label])=>field(label,key,value.limits[key]??'','number',`required min="${['rounds','revisions'].includes(key)?0:key==='cost'?0.01:1}" step="${key==='cost'?'0.01':'1'}"`)).join('')}</div><p class="muted">全部角色共用任务预算，费用币种种类跟随模型配置。</p>`,'保存上限',values=>{for(const [key] of limits)value.limits[key]=Number(values.get(key));save(config,'修改研究与修订上限')});return true;
    }
    if(action==='cap-contract') {
      openDialog('业务输入与输出',`<div class="form">${[['goal','业务目标'],['documents','支持文档'],['inputs','必需输入'],['outputs','输出清单'],['checkpoints','人工检查点']].map(([key,label])=>`<label class="field">${label}<textarea name="${key}" required>${escape(value[key])}</textarea></label>`).join('')}<label class="field">默认资料集<select name="collection">${options([['','创建任务时确认'],...ui.collections().map(x=>[`${x.id}:${x.version}`,`${x.name} v${x.version}`])],value.defaultCollection)}</select></label></div>`,'保存业务约定',values=>{for(const key of ['goal','documents','inputs','outputs','checkpoints']){if(!values.get(key).trim()){dialogError('请填写全部必需业务信息。');return}}for(const key of ['goal','documents','inputs','outputs','checkpoints'])value[key]=values.get(key).trim();value.defaultCollection=values.get('collection');save(config,'修改业务输入与输出')});return true;
    }
    throw new Error('能力操作不存在');
  }
  return {render,summary,check,handle};
}
