const WorkspaceUI = (() => {
  const escape=value=>String(value??'').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;').replaceAll("'",'&#39;');
  const button=(label,action,value='',primary=false,disabled=false)=>`<button type="button" class="btn ${primary?'primary':''}" data-action="${action}" data-value="${escape(value)}" ${disabled?'disabled':''}>${escape(label)}</button>`;
  const tag=(label,tone='neutral')=>`<span class="status ${tone}">${escape(label)}</span>`;
  const options=(items,selected)=>items.map(([value,label])=>`<option value="${escape(value)}" ${String(value)===String(selected)?'selected':''}>${escape(label)}</option>`).join('');
  const title=(name,actions='')=>`<div class="heading"><h1>${escape(name)}</h1><div class="actions">${actions}</div></div>`;
  const empty=(name,description,action='')=>`<div class="empty-message"><strong>${escape(name)}</strong>${escape(description)}${action?`<div>${action}</div>`:''}</div>`;
  const table=(head,rows)=>`<div class="table-wrap"><table class="table"><thead><tr>${head.map(x=>`<th>${x}</th>`).join('')}</tr></thead><tbody>${rows.join('')}</tbody></table></div>`;
  const tabs=(items,current,action,label)=>`<nav class="product-tabs shared-tabs" aria-label="${escape(label)}">${items.map(([id,name])=>`<button type="button" data-action="${action}" data-value="${escape(id)}" aria-pressed="${current===id}">${escape(name)}</button>`).join('')}</nav>`;
  const field=(label,name,value='',type='text',extra='')=>`<label class="field">${escape(label)}<input name="${name}" type="${type}" value="${escape(value)}" ${extra}></label>`;
  const pagination=(count,page,size,action)=>{const pages=Math.max(1,Math.ceil(count/size));return `<div class="pagination"><span>共 ${count} 条</span>${button('上一页',action,page-1,false,page<=1)}<span>第 ${page} / ${pages} 页</span>${button('下一页',action,page+1,false,page>=pages)}</div>`};
  function presentPage(root){
    const descriptions={
      S01:'.agent-launch>p',
      S03:'.screen-content>.inline-actions',
      S04:'.library-side-note,.library-progress>div>p,.knowledge-panel>.panel-top>div>p',
      S05:'.library-side-note',
      S07:'.plan-row p,.setup-grid>.card>.source-excerpt,.setup-grid>.card>.alert.info,.research-scope-summary>p.muted',
      S13:'.config-policy,.agent-config-body>.panel-top>div>p,.agent-config-body>.section-intro p,.agent-config-body>.source-excerpt,.agent-config-body>.alert.info,.api-section-heading>div>p',
      S16:'.screen-content>.card>p.muted',
      S18:'.knowledge-usage>p,.project-grid>div>.card>.panel-top>span.muted'
    };
    root.querySelectorAll('.heading p').forEach(node=>node.remove());
    if(descriptions[root.id])root.querySelectorAll(descriptions[root.id]).forEach(node=>node.remove());
  }
  return Object.freeze({escape,button,tag,options,title,empty,table,tabs,field,pagination,presentPage});
})();
