(function(){
  window.deleteFamily = function(id){
    try{
      var f = window.db && db.families ? db.families.find(function(x){return x.id===id;}) : null;
      if(!f) return;
      var modal = document.getElementById('modal');
      var title = document.getElementById('modalTitle');
      var body = document.getElementById('modalBody');
      if(!modal || !title || !body){
        if(window.confirm && window.confirm('سيتم حذف عائلة «'+f.name+'» بالكامل. هل تريد المتابعة؟')) window.confirmDeleteFamily(id);
        return;
      }
      title.textContent='تأكيد حذف العائلة';
      body.innerHTML='<div class="dangerbox"><b>سيتم حذف عائلة «'+String(f.name).replace(/[&<>\"]/g,function(m){return {"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;"}[m];})+'» بالكامل.</b><div style="margin-top:6px">سيتم أيضاً حذف سجلات استلامها من جميع الأشهر. هذا الإجراء لا يمكن التراجع عنه.</div></div><div class="row" style="margin-top:12px"><button id="deleteFamilyConfirmBtn" class="btn btn-danger">نعم، احذف العائلة بالكامل</button><button id="deleteFamilyCancelBtn" class="btn btn-secondary">إلغاء</button></div>';
      var ok=document.getElementById('deleteFamilyConfirmBtn');
      var cancel=document.getElementById('deleteFamilyCancelBtn');
      if(ok) ok.onclick=function(){ window.confirmDeleteFamily(id); };
      if(cancel) cancel.onclick=function(){ window.closeModal(); };
      modal.classList.add('show');
    }catch(e){
      console.error('deleteFamily fix',e);
      try{ alert('تعذر فتح نافذة الحذف. حاول مرة أخرى.'); }catch(ignore){}
    }
  };
})();
