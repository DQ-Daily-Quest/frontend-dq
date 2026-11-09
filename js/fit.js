/* 모바일 폭 대응: 디자인 기준 폭(390px)보다 좁은 기기에서는 화면 전체를 폭에 맞춰 축소 */
(function () {
  var BASE = 390;
  function fit() {
    var w = window.innerWidth, z = w < BASE ? w / BASE : 1;
    var root = document.documentElement;
    root.style.zoom = z === 1 ? '' : z;
    root.style.setProperty('--app-h', (window.innerHeight / z) + 'px');
  }
  fit();
  window.addEventListener('resize', fit);
})();
