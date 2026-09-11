/**
 * NGYT ad-blocker for m.youtube.com (v2).
 *
 * Injected by NGYTWebViewClient via evaluateJavascript on page start /
 * finish / progress change. Guards against double-install so repeated
 * injection is safe (YouTube is a single-page app).
 *
 * Strategy (client-side only):
 *  1. CSS: hide banner / overlay / promoted / masthead ad containers.
 *  2. JS: detect video ads via the player API + ad marker classes, then
 *     mute, 16x speed and hammer-seek to the end until the ad is gone.
 *     Mute/speed are restored on the real video afterwards.
 *  3. Click skip buttons AND overlay close buttons whenever visible.
 *  4. MutationObserver + 250 ms interval: re-apply as YouTube rewrites the DOM.
 */
(function () {
  'use strict';

  // Avoid installing twice when the client re-injects on SPA navigation.
  if (window.__ngytAdblockInstalled) {
    return;
  }
  window.__ngytAdblockInstalled = true;

  /** CSS selectors for banner / overlay / promoted ad containers. */
  var HIDE_SELECTORS = [
    '.ytp-ad-module',
    '.ytp-ad-player-overlay',
    '.ytp-ad-image-overlay',
    '.ytp-ad-text-overlay',
    '.ytp-ad-preview-container',
    '.ytp-ad-preview-text',
    '.ytp-ad-survey-container',
    'ytd-display-ad-renderer',
    'ytd-promoted-sparkles-web-renderer',
    'ytd-promoted-video-renderer',
    'ytd-action-companion-ad-renderer',
    'ytd-companion-slot-renderer',
    'ytd-in-feed-ad-layout-renderer',
    'ytd-ad-slot-renderer',
    'ytd-player-legacy-desktop-watch-ads-renderer',
    '#masthead-ad',
    '.ytd-masthead-ad-v3-renderer',
    '#player-ads',
    '#mealbar-promo-renderer'
  ];

  /** Skip buttons + overlay close buttons (all YouTube variants). */
  var CLICK_SELECTORS = [
    '.ytp-ad-skip-button',
    '.ytp-skip-ad-button',
    '.ytp-ad-skip-button-modern',
    '.ytp-ad-overlay-close-button',
    '.ytp-ad-image-overlay-close-button'
  ];

  /** 16x burns through unskippable ads that resist seeking. */
  var AD_BURN_RATE = 16;

  /** Inject a <style> tag hiding known ad containers. */
  function injectCss() {
    if (document.getElementById('__ngyt-adblock-css')) {
      return;
    }
    var css = HIDE_SELECTORS.join(', ') + ' { display: none !important; }';
    var style = document.createElement('style');
    style.id = '__ngyt-adblock-css';
    style.textContent = css;
    (document.head || document.documentElement).appendChild(style);
  }

  /** Click any visible skip / close button. */
  function clickAdButtons() {
    var btns = document.querySelectorAll(CLICK_SELECTORS.join(', '));
    for (var i = 0; i < btns.length; i++) {
      try {
        var b = btns[i];
        if (b && b.offsetParent !== null) {
          b.click();
        }
      } catch (e) { /* ignore */ }
    }
  }

  /**
   * True when a video ad is playing. Uses only strong signals (player API
   * state + ad marker classes) so the real video is never mistaken for an ad.
   */
  function isAdPlaying(player) {
    try {
      // YouTube player API: ad state 1 == ad currently playing.
      if (player && typeof player.getAdState === 'function' && player.getAdState() === 1) {
        return true;
      }
    } catch (e) { /* API unavailable: fall through to class checks */ }
    if (player && (player.classList.contains('ad-showing')
        || player.classList.contains('ad-interrupting'))) {
      return true;
    }
    var videos = document.querySelectorAll('video');
    for (var i = 0; i < videos.length; i++) {
      var c = videos[i].classList;
      if (c.contains('ad-showing') || c.contains('ad-interrupting')) {
        return true;
      }
    }
    return false;
  }

  /** Mute + 16x + seek-to-end on every video while an ad plays. */
  function neutralizeAd(player) {
    var videos = document.querySelectorAll('video');
    for (var i = 0; i < videos.length; i++) {
      try {
        var v = videos[i];
        // Mute (remember if WE muted, so we can restore later).
        if (!v.muted) {
          v.muted = true;
          v.__ngytMuted = true;
        }
        // Max speed: unskippable ads finish in ~2 s even if seeks are clamped.
        if (v.playbackRate !== AD_BURN_RATE) {
          if (!v.__ngytRate) {
            v.__ngytRate = v.playbackRate || 1;
          }
          try { v.playbackRate = AD_BURN_RATE; } catch (e2) { /* ignore */ }
        }
        // Hammer the seek: some ads clamp currentTime, so retry every sweep.
        try {
          if (isFinite(v.duration) && v.duration > 0) {
            v.currentTime = Math.max(0, v.duration - 0.05);
          }
        } catch (e3) { /* DASH/live: ignore */ }
        if (v.paused) {
          var p = v.play();
          if (p && p.catch) { p.catch(function () {}); }
        }
      } catch (e) { /* ignore */ }
    }
    // Player-API route as backup (seekTo + mute + keep playing).
    try {
      if (player && typeof player.mute === 'function' && typeof player.isMuted === 'function') {
        if (!player.isMuted()) {
          player.mute();
          player.__ngytApiMuted = true;
        }
      }
      if (player && typeof player.seekTo === 'function' && typeof player.getDuration === 'function') {
        var d = player.getDuration();
        if (d > 0) {
          player.seekTo(d, true);
        }
      }
      if (player && typeof player.playVideo === 'function'
          && typeof player.getPlayerState === 'function' && player.getPlayerState() === 2) {
        player.playVideo();
      }
    } catch (e4) { /* API unavailable: element route above covers it */ }
  }

  /** Restore mute/speed we applied once the ad is gone. */
  function restorePlayback(player) {
    var videos = document.querySelectorAll('video');
    for (var i = 0; i < videos.length; i++) {
      try {
        var v = videos[i];
        if (v.__ngytMuted) {
          v.muted = false;
          v.__ngytMuted = false;
        }
        if (v.__ngytRate) {
          try { v.playbackRate = v.__ngytRate; } catch (e2) { /* ignore */ }
          v.__ngytRate = 0;
        }
      } catch (e) { /* ignore */ }
    }
    try {
      if (player && player.__ngytApiMuted && typeof player.unMute === 'function') {
        player.unMute();
        player.__ngytApiMuted = false;
      }
    } catch (e3) { /* ignore */ }
  }

  /** Remove overlay / banner ad nodes that slip past CSS. */
  function removeAdNodes() {
    var nodes = document.querySelectorAll(HIDE_SELECTORS.join(', '));
    for (var i = 0; i < nodes.length; i++) {
      try {
        var n = nodes[i];
        if (n && n.parentNode) {
          n.parentNode.removeChild(n);
        }
      } catch (e) { /* ignore */ }
    }
  }

  /** One sweep: CSS + buttons + ad neutralize/restore + node removal. */
  function sweep() {
    injectCss();
    clickAdButtons();
    var player = document.getElementById('movie_player');
    if (isAdPlaying(player)) {
      neutralizeAd(player);
    } else {
      restorePlayback(player);
    }
    removeAdNodes();
  }

  // Initial sweep.
  sweep();

  // Re-sweep on DOM changes (YouTube rewrites nodes on navigation).
  try {
    var observer = new MutationObserver(function () {
      sweep();
    });
    observer.observe(document.documentElement, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['class', 'src']
    });
  } catch (e) { /* MutationObserver unavailable: interval below covers it */ }

  // Tight loop: hammer-seeks unskippable ads so they end in ~2 s.
  setInterval(sweep, 250);
})();
