/* Daily Quest — 퍼블리싱용 공통 인터랙션 */
(function () {
  // 글자수 카운터: <textarea maxlength> + .textarea-box__count
  document.querySelectorAll('.textarea-box').forEach(function (box) {
    var ta = box.querySelector('textarea');
    var cnt = box.querySelector('.textarea-box__count');
    if (!ta || !cnt) return;
    var max = ta.getAttribute('maxlength');
    var update = function () { cnt.textContent = ta.value.length + ' / ' + max; };
    ta.addEventListener('input', update);
    update();
  });

  // 단일 선택 그룹: [data-select="single"] 안의 [data-option]
  document.querySelectorAll('[data-select="single"]').forEach(function (group) {
    group.addEventListener('click', function (e) {
      var opt = e.target.closest('[data-option]');
      if (!opt || !group.contains(opt)) return;
      group.querySelectorAll('[data-option]').forEach(function (o) {
        o.classList.toggle('is-active', o === opt);
        if (o.hasAttribute('aria-selected')) o.setAttribute('aria-selected', o === opt);
        if (o.hasAttribute('aria-pressed')) o.setAttribute('aria-pressed', o === opt);
      });
    });
  });

  // 다중 선택: [data-select="multi"] 안의 [data-option]
  document.querySelectorAll('[data-select="multi"]').forEach(function (group) {
    group.addEventListener('click', function (e) {
      var opt = e.target.closest('[data-option]');
      if (!opt || !group.contains(opt)) return;
      var on = !opt.classList.contains('is-active');
      opt.classList.toggle('is-active', on);
      if (opt.hasAttribute('aria-pressed')) opt.setAttribute('aria-pressed', on);
    });
  });

  // 비밀번호 보기 토글: [data-toggle-password]
  document.querySelectorAll('[data-toggle-password]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var input = btn.parentElement.querySelector('input');
      if (input) input.type = input.type === 'password' ? 'text' : 'password';
    });
  });
})();

/* 코인 스테퍼: [data-stepper] */
(function () {
  document.querySelectorAll('[data-stepper]').forEach(function (st) {
    var out = st.querySelector('[data-value]');
    var min = +st.dataset.min, max = +st.dataset.max, step = +st.dataset.step, bal = +st.dataset.balance;
    var helper = document.querySelector('[data-stepper-helper]');
    var set = function (v) {
      v = Math.max(min, Math.min(max, v));
      out.textContent = v;
      if (helper && bal) helper.textContent = '내 코인 ' + bal + ' → ' + (bal - v) + ' (미션 완료 시 차감)';
    };
    st.querySelector('[data-dec]').addEventListener('click', function () { set(+out.textContent - step); });
    st.querySelector('[data-inc]').addEventListener('click', function () { set(+out.textContent + step); });
  });
})();
