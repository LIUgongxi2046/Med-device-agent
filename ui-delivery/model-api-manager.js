function createModelApiManager(ui) {
  'use strict';
  const {escape,button,tag,table,empty,options,clockText,projects,versions,openDialog,closeProductDialog,dialogError,log,refresh,showFeedback}=ui;
  const records=[],credentials=new Map();
  const filters={query:'',status:'',page:1,size:10};
  let sequence=0,selected=null;
  const protocols=[['openai-chat','OpenAI 兼容 · Chat Completions'],['openai-responses','OpenAI · Responses'],['anthropic','Anthropic · Messages'],['custom','自定义 HTTP API']];
  const authTypes=[['bearer','Bearer API Key'],['header','自定义密钥请求头'],['none','无需鉴权']];
  const find=id=>{const record=records.find(item=>item.id===id);if(!record)throw new Error('模型 API 配置不存在');return record};
  const protocolName=value=>protocols.find(([id])=>id===value)[1];
  const refs=record=>versions.filter(version=>version.model===record.id);
  const ready=record=>record.auth==='none'||credentials.has(record.id)||Boolean(record.credentialRef);
  const status=record=>record.disabled?'已停用':!ready(record)?'待配置凭据':record.verified?'已验证':'未验证';
  const tone=record=>record.disabled?'neutral':record.verified?'ok':'warn';
  const usable=value=>value==='演示连接'||records.some(record=>record.id===value&&!record.disabled&&record.verified&&ready(record));
  function invalidate(record) {
    record.verified=false;record.lastCheck='';
    refs(record).filter(version=>version.status==='草稿').forEach(version=>version.checked=false);
  }
  function audit(record,action,detail) {
    record.history.unshift({time:clockText(),revision:record.revision,action,detail,person:'林知远'});
    log(action,'',`${record.name} · v${record.revision}`);
  }
  function scopeLabel(record) {
    return record.scope==='company'?'当前企业全部项目':record.projectIds.map(id=>projects.find(project=>project.id===id).name).join('、');
  }
  function field(label,name,value='',type='text',extra='',hint='') {
    return `<label class="field">${label}<input type="${type}" name="${name}" value="${escape(value)}" ${extra}>${hint?`<span class="muted small">${hint}</span>`:''}</label>`;
  }
  function editor(id='') {
    const current=id?find(id):null;
    const record=current||{name:'',provider:'',protocol:'openai-chat',modelId:'',baseUrl:'',auth:'bearer',header:'x-api-key',credentialRef:'',scope:'company',projectIds:[],license:'',timeout:60,maxOutput:4096,rpm:60,tpm:60000,retries:0,budget:'',currency:'CNY'};
    openDialog(current?'编辑模型 API 配置':'新增模型 API 配置',`
      <div class="api-form-note">配置与凭据仅保留在当前页面会话，刷新后清除。配置名称、服务提供方、Model ID、Base URL 和许可说明必填。保存配置后需要验证连接。</div>
      <section class="api-form-section"><h3>基本信息</h3><div class="form two">
        ${field('配置名称','name',record.name,'text','required maxlength="60"')}
        ${field('服务提供方','provider',record.provider,'text','required maxlength="80"')}
        <label class="field">接口协议<select name="protocol">${options(protocols,record.protocol)}</select></label>
        ${field('Model ID','modelId',record.modelId,'text','required maxlength="200"','填写服务提供方的模型标识。')}
        <label class="field wide">Base URL<input name="baseUrl" type="url" required value="${escape(record.baseUrl)}" placeholder="输入服务提供方的接口基础地址"><span class="muted small">使用 HTTPS 地址；内部服务可填写 HTTP 地址。地址中不包含密钥、查询参数或身份凭据。</span></label>
      </div></section>
      <section class="api-form-section"><h3>身份凭据</h3><div class="form two">
        <label class="field">鉴权方式<select name="auth" data-api-change="auth">${options(authTypes,record.auth)}</select></label>
        <label class="field" data-api-field="header" ${record.auth==='header'?'':'hidden'}>密钥请求头<input name="header" value="${escape(record.header)}" maxlength="80" ${record.auth==='header'?'required':''}></label>
        <label class="field" data-api-field="credential" ${record.auth==='none'?'hidden':''}>API Key<input name="apiKey" type="password" autocomplete="new-password" spellcheck="false" placeholder="${current&&credentials.has(current.id)?'留空保留本次会话中的凭据':'填写 API Key，或登记下方凭据引用'}"><span class="muted small">密钥不回显、不写入日志或导出文件。</span></label>
        <label class="field wide" data-api-field="reference" ${record.auth==='none'?'hidden':''}>凭据引用（选填）<input name="credentialRef" value="${escape(record.credentialRef)}" maxlength="160" placeholder="输入企业凭据管理中的引用名称"><span class="muted small">仅填写引用名称时，仍需凭据服务解析并完成验证。</span></label>
      </div></section>
      <section class="api-form-section"><h3>调用限制</h3><div class="form two">
        ${field('请求超时（秒）','timeout',record.timeout,'number','required min="1" max="600" step="1"')}
        ${field('最大输出 Token','maxOutput',record.maxOutput,'number','required min="1" max="1000000" step="1"')}
        ${field('每分钟请求上限','rpm',record.rpm,'number','required min="1" max="1000000" step="1"')}
        ${field('每分钟 Token 上限','tpm',record.tpm,'number','required min="1" max="100000000" step="1"')}
        ${field('失败重试次数','retries',record.retries,'number','required min="0" max="5" step="1"','实际调用前核对重试费用与停止条件。')}
        ${field('每日预算（选填）','budget',record.budget,'number','min="0.01" step="0.01"','未填写时显示预算未配置，不表示无限额。')}
        <label class="field">预算币种<select name="currency">${options([['CNY','人民币 CNY'],['USD','美元 USD']],record.currency)}</select></label>
      </div></section>
      <section class="api-form-section"><h3>授权与许可</h3><div class="form">
        <label class="field">适用范围<select name="scope" data-api-change="scope">${options([['company','当前企业全部项目'],['projects','指定项目']],record.scope)}</select></label>
        <fieldset class="api-projects" data-api-field="projects" ${record.scope==='projects'?'':'hidden'}><legend>选择可使用的项目</legend>${projects.map(project=>`<label><input name="projectIds" type="checkbox" value="${project.id}" ${record.projectIds.includes(project.id)?'checked':''}>${escape(project.name)}</label>`).join('')}</fieldset>
        <label class="field">许可与数据使用说明<textarea name="license" required maxlength="1000" placeholder="说明获准用途、允许发送的数据范围及责任人">${escape(record.license)}</textarea></label>
      </div></section>`,'保存配置',values=>{
        const name=values.get('name').trim(),baseUrl=values.get('baseUrl').trim(),apiKey=values.get('apiKey').trim();
        if(!name||!values.get('provider').trim()||!values.get('modelId').trim()||!values.get('license').trim()){dialogError('请填写配置名称、服务提供方、Model ID 和许可说明。');return}
        if(records.some(item=>item.id!==id&&item.name.toLocaleLowerCase()===name.toLocaleLowerCase())){dialogError('配置名称已存在，请使用不同名称。');return}
        if(!URL.canParse(baseUrl)){dialogError('Base URL 格式无效。');return}
        const url=new URL(baseUrl);
        if(!['https:','http:'].includes(url.protocol)||url.username||url.password||url.search||url.hash){dialogError('请填写 HTTP 或 HTTPS 基础地址，移除地址中的凭据、查询参数和片段标识。');return}
        if(apiKey&&baseUrl.includes(apiKey)){dialogError('接口地址不能包含 API Key。');return}
        const projectIds=values.getAll('projectIds');
        if(values.get('scope')==='projects'&&!projectIds.length){dialogError('请至少选择一个可使用的项目。');return}
        const header=values.get('header').trim();
        if(values.get('auth')==='header'&&!/^[A-Za-z][A-Za-z0-9-]*$/.test(header)){dialogError('请求头名称只能包含英文字母、数字和连字符，且以字母开头。');return}
        const numeric={timeout:[1,600],maxOutput:[1,1000000],rpm:[1,1000000],tpm:[1,100000000],retries:[0,5]};
        for(const [key,[min,max]] of Object.entries(numeric)){const value=Number(values.get(key));if(!Number.isInteger(value)||value<min||value>max){dialogError('调用限制数值超出范围。');return}}
        const next={id:current?.id||`api-${++sequence}`,name,provider:values.get('provider').trim(),protocol:values.get('protocol'),modelId:values.get('modelId').trim(),baseUrl:url.href.replace(/\/$/,''),auth:values.get('auth'),header,credentialRef:values.get('auth')==='none'?'':values.get('credentialRef').trim(),scope:values.get('scope'),projectIds:values.get('scope')==='projects'?projectIds:[],license:values.get('license').trim(),timeout:Number(values.get('timeout')),maxOutput:Number(values.get('maxOutput')),rpm:Number(values.get('rpm')),tpm:Number(values.get('tpm')),retries:Number(values.get('retries')),budget:values.get('budget')?Number(values.get('budget')):null,currency:values.get('currency'),revision:(current?.revision||0)+1,verified:false,disabled:current?.disabled||false,updated:clockText(),history:current?.history||[],snapshots:current?.snapshots||[]};
        if(current){current.snapshots.unshift({...current,history:undefined,snapshots:undefined});Object.assign(current,next)}else records.unshift(next);
        if(next.auth==='none')credentials.delete(next.id);else if(apiKey)credentials.set(next.id,apiKey);
        const saved=find(next.id);audit(saved,current?'更新模型 API 配置':'创建模型 API 配置','配置等待连接验证');
        invalidate(saved);
        selected=saved.id;filters.page=1;closeProductDialog();refresh();showFeedback('模型 API 配置已保存在本次会话，连接尚未验证。');
      });
  }
  function facts(record,historical=false) {
    const credentialState=historical?'历史版本不保存密钥':record.auth==='none'?'无需鉴权':credentials.has(record.id)?'已设置 · 本次会话':record.credentialRef?'已登记引用 · 等待解析':'尚未配置';
    return `<div class="api-facts">${[['服务提供方',record.provider],['接口协议',protocolName(record.protocol)],['Base URL',record.baseUrl],['Model ID',record.modelId],['鉴权方式',authTypes.find(([id])=>id===record.auth)[1]],['凭据状态',credentialState],['凭据引用',record.credentialRef||'未登记'],['请求超时',`${record.timeout} 秒`],['最大输出',`${record.maxOutput} Token`],['请求与 Token 上限',`${record.rpm} RPM / ${record.tpm} TPM`],['失败重试',`${record.retries} 次`],['每日预算',record.budget===null?'未配置':`${record.budget} ${record.currency}`],['授权范围',scopeLabel(record)],['许可说明',record.license]].map(([label,value])=>`<div><span>${label}</span><strong>${escape(value)}</strong></div>`).join('')}</div>`;
  }
  function detail() {
    const record=find(selected),related=refs(record);
    return `<div class="api-detail-toolbar">${button('返回连接列表','api-list')}<div class="actions">${button('编辑配置','api-edit',record.id)}${button('设置凭据','api-credential',record.id)}${button('验证连接','api-verify',record.id,true,record.disabled)}</div></div><div class="api-detail-layout"><section class="card pad"><div class="panel-top"><div><h2>${escape(record.name)}</h2><p>配置 v${record.revision} · 更新于 ${escape(record.updated)}</p></div>${tag(status(record),tone(record))}</div>${facts(record)}</section><aside class="api-detail-side"><section class="card pad"><h2>连接验证</h2><p class="muted">${record.lastCheck?escape(record.lastCheck):'尚未执行连接验证。'}</p><div class="alert info">当前验证服务未接入，未发送 API 请求，也未产生调用费用。</div><div class="api-detail-actions">${button('验证连接','api-verify',record.id,false,record.disabled)}${button(record.disabled?'恢复配置':'停用配置',record.disabled?'api-restore':'api-disable',record.id)}${button('清除凭据','api-clear',record.id,false,!credentials.has(record.id)&&!record.credentialRef)}</div></section><section class="card pad"><h2>关联 Agent</h2>${related.length?related.map(version=>`<div class="requirement-row"><div><strong>${escape(version.name)}</strong><p>${escape(version.id)} · ${escape(version.status)}</p></div>${button('查看','api-agent',version.id)}</div>`).join(''):'<p class="muted">暂无 Agent 使用此连接。验证并启用后，可在 Agent 的“模型与预算”中选择。</p>'}</section></aside></div><section class="card pad api-history"><div class="panel-top"><h2>版本与操作记录</h2>${button('删除配置','api-delete',record.id,false,related.length>0)}</div>${record.history.map(entry=>`<article class="history-entry"><strong>${escape(entry.action)} · v${entry.revision}</strong><p>${escape(entry.detail)}</p><span class="muted small">${escape(entry.time)} · ${escape(entry.person)}</span></article>`).join('')}${record.snapshots.length?`<h3>历史配置</h3><div class="actions">${record.snapshots.map(snapshot=>button(`查看 v${snapshot.revision}`,'api-version',`${record.id}:${snapshot.revision}`)).join('')}</div>`:''}</section>`;
  }
  function render() {
    if(selected)return detail();
    const matched=records.filter(record=>(!filters.query||[record.name,record.provider,record.modelId,record.baseUrl].some(value=>value.toLowerCase().includes(filters.query.toLowerCase())))&&(!filters.status||status(record)===filters.status));
    const pages=Math.max(1,Math.ceil(matched.length/filters.size));filters.page=Math.min(filters.page,pages);
    return `<div class="api-overview">${[['全部连接',records.length],['等待验证',records.filter(record=>!record.disabled&&!record.verified).length],['已验证并启用',records.filter(record=>usable(record.id)).length],['已停用',records.filter(record=>record.disabled).length]].map(([label,count])=>`<div><span>${label}</span><strong>${count}</strong></div>`).join('')}</div><section class="card pad"><div class="panel-top"><div><h2>模型 API 连接</h2><p>管理服务地址、凭据、调用限制与项目授权。</p></div>${button('新增 API 配置','api-new','',true)}</div><form class="toolbar api-search" data-form="api-search"><label>搜索连接<input name="query" value="${escape(filters.query)}" placeholder="名称、提供方、Model ID 或地址"></label><label>连接状态<select name="status">${options([['','全部状态'],...['待配置凭据','未验证','已验证','已停用'].map(value=>[value,value])],filters.status)}</select></label><button class="btn primary" type="submit">查询</button>${button('重置','api-reset')}</form>${matched.length?table(['连接与提供方','模型与协议','授权范围','状态','更新版本','操作'],matched.slice((filters.page-1)*filters.size,filters.page*filters.size).map(record=>`<tr data-api-row="${record.id}"><td><button class="quiet-link" data-action="api-open" data-value="${record.id}">${escape(record.name)}</button><small>${escape(record.provider)}</small></td><td><strong>${escape(record.modelId)}</strong><small>${escape(protocolName(record.protocol))}</small></td><td>${record.scope==='company'?'当前企业':`${record.projectIds.length} 个项目`}</td><td>${tag(status(record),tone(record))}</td><td>v${record.revision}<small>${escape(record.updated)}</small></td><td>${button('详情','api-open',record.id)}${button('编辑','api-edit',record.id)}</td></tr>`)):empty(records.length?'没有符合条件的连接':'尚未登记模型 API','新增连接后，填写服务地址、模型标识和授权范围，完成验证后供 Agent 使用。',button('新增 API 配置','api-new'))}<div class="pagination"><span>共 ${matched.length} 条</span><label>每页 <select data-api-change="size" aria-label="模型连接每页条数">${options([['10','10 条'],['20','20 条'],['50','50 条']],filters.size)}</select></label>${button('上一页','api-page',filters.page-1,false,filters.page===1)}<span>第 ${filters.page} / ${pages} 页</span>${button('下一页','api-page',filters.page+1,false,filters.page===pages)}</div></section><p class="api-session-note">当前会话保存配置；刷新页面后清除。连接验证与正式调用等待服务接入。</p>`;
  }
  function handle(action,value) {
    if(!action.startsWith('api-'))return false;
    if(action==='api-home'){selected=null;ui.open();return true}
    if(action==='api-list'){selected=null;refresh();return true}
    if(action==='api-new'||action==='api-edit'){editor(action==='api-edit'?value:'');return true}
    if(action==='api-open'){find(value);selected=value;refresh();return true}
    if(action==='api-reset'){Object.assign(filters,{query:'',status:'',page:1});refresh();return true}
    if(action==='api-page'){filters.page=Number(value);refresh();return true}
    if(action==='api-agent'){ui.openAgent(value);return true}
    if(action==='api-version'){const [id,revision]=value.split(':');const record=find(id).snapshots.find(item=>item.revision===Number(revision));if(!record)throw new Error('配置版本不存在');openDialog(`${record.name} · v${record.revision}`,facts(record,true),'完成查看');return true}
    const record=find(value);
    if(action==='api-credential'){
      if(record.auth==='none'){editor(record.id);return true}
      openDialog('设置 API 凭据',`<p>${escape(record.name)} · v${record.revision}</p><div class="form"><label class="field">API Key<input name="apiKey" type="password" autocomplete="new-password" required placeholder="输入新的 API Key"></label><p class="muted small">凭据只保留在当前页面会话，刷新后清除。设置后需要重新验证连接。</p></div>`,'保存凭据',values=>{const key=values.get('apiKey').trim();if(!key){dialogError('请输入 API Key。');return}credentials.set(record.id,key);invalidate(record);record.updated=clockText();audit(record,'更新 API 凭据','凭据已更新，等待重新验证');closeProductDialog();refresh()});return true;
    }
    if(action==='api-verify'){
      const message=record.disabled?'此连接已停用，请恢复后重新验证。':!ready(record)?'请设置 API Key 或登记凭据引用，再进行连接验证。':'连接验证服务尚未接入，未发送请求。配置保持未验证状态。';
      record.lastCheck=message;audit(record,'查看连接验证条件',message);refresh();
      openDialog('连接验证',`<div class="alert ${!ready(record)?'':'info'}">${message}</div><div class="api-verification"><div><span>接口配置</span>${tag('已登记','info')}</div><div><span>凭据准备</span>${tag(ready(record)?'已登记，待服务核验':'等待配置',ready(record)?'info':'warn')}</div><div><span>连接请求</span>${tag('未发送')}</div><div><span>认证与模型可用性</span>${tag('未验证','warn')}</div><div><span>延迟、Token 与费用</span><strong>未产生</strong></div></div>`,'完成查看');return true;
    }
    if(['api-disable','api-restore','api-delete','api-clear'].includes(action)) {
      if(action==='api-delete'&&refs(record).length){showFeedback('此配置已被 Agent 引用，无法删除。');return true}
      const label={'api-disable':'停用配置','api-restore':'恢复配置','api-delete':'删除配置','api-clear':'清除凭据'}[action];
      openDialog(label,`<p>${escape(record.name)} · v${record.revision}</p><p>${action==='api-disable'?`关联 ${refs(record).length} 个 Agent 版本。停用后不能供新任务使用。`:action==='api-delete'?'删除后当前会话无法恢复此配置。':action==='api-clear'?'清除本次会话中的密钥和凭据引用，连接需要重新配置。':'恢复后保持未验证，需要重新验证才能用于 Agent。'}</p><label class="field">操作原因<textarea name="reason" required></textarea></label><label class="api-confirm"><input name="confirm" type="checkbox" required>确认处理当前配置</label>`,'确认'+label,values=>{
        if(!values.get('reason').trim()){dialogError('请填写操作原因。');return}
        if(action==='api-delete'){records.splice(records.indexOf(record),1);credentials.delete(record.id);selected=null;log('删除模型 API 配置','',record.name)}
        else {if(action==='api-clear'){credentials.delete(record.id);record.credentialRef=''}else record.disabled=action==='api-disable';invalidate(record);record.updated=clockText();audit(record,label,values.get('reason'))}
        closeProductDialog();refresh();
      });return true;
    }
    throw new Error('未知模型 API 操作');
  }
  function change(target) {
    if(target.dataset.apiChange==='size'){filters.size=Number(target.value);filters.page=1;refresh();return true}
    if(target.dataset.apiChange==='scope'){target.form.querySelector('[data-api-field="projects"]').hidden=target.value!=='projects';return true}
    if(target.dataset.apiChange==='auth'){
      const form=target.form;
      form.querySelector('[data-api-field="header"]').hidden=target.value!=='header';form.querySelector('[name="header"]').required=target.value==='header';
      for(const field of ['credential','reference'])form.querySelector(`[data-api-field="${field}"]`).hidden=target.value==='none';return true;
    }
    return false;
  }
  return {render,handle,change,usable,search:values=>{filters.query=values.get('query').trim();filters.status=values.get('status');filters.page=1;refresh()},selectOptions:current=>options([...(current==='演示连接'?[['演示连接','演示连接 · 当前原型']]:[['','请选择已验证连接']]),...records.filter(record=>usable(record.id)).map(record=>[record.id,`${record.name} · ${record.modelId} · v${record.revision}`])],current),selectionHint:()=>`已验证并启用 ${records.filter(record=>usable(record.id)).length} 项；新连接通过验证后可供 Agent 选择。`};
}
