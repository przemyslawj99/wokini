const CONSENT_KEY = 'wokini-cookie-consent-v1';

function saveConsent(preferences) {
  localStorage.setItem(CONSENT_KEY, JSON.stringify(preferences));
  window.dispatchEvent(new CustomEvent('cookieConsentUpdated', { detail: preferences }));
}

function readConsent() {
  try {
    const saved = localStorage.getItem(CONSENT_KEY);
    return saved ? JSON.parse(saved) : null;
  } catch {
    return null;
  }
}

function initCookieBanner() {
  const banner = document.getElementById('cookie-banner');
  const analytics = document.getElementById('analytics-consent');
  const marketing = document.getElementById('marketing-consent');
  const acceptAll = document.getElementById('accept-all');
  const savePreferences = document.getElementById('save-preferences');
  const rejectOptional = document.getElementById('reject-optional');
  const openSettings = document.getElementById('cookie-settings');

  const current = readConsent();

  if (!current) {
    banner.hidden = false;
  } else {
    analytics.checked = Boolean(current.analytics);
    marketing.checked = Boolean(current.marketing);
  }

  acceptAll.addEventListener('click', () => {
    analytics.checked = true;
    marketing.checked = true;
    saveConsent({ necessary: true, analytics: true, marketing: true, ts: Date.now() });
    banner.hidden = true;
  });

  savePreferences.addEventListener('click', () => {
    saveConsent({
      necessary: true,
      analytics: analytics.checked,
      marketing: marketing.checked,
      ts: Date.now(),
    });
    banner.hidden = true;
  });

  rejectOptional.addEventListener('click', () => {
    analytics.checked = false;
    marketing.checked = false;
    saveConsent({ necessary: true, analytics: false, marketing: false, ts: Date.now() });
    banner.hidden = true;
  });

  openSettings.addEventListener('click', () => {
    const fresh = readConsent();
    analytics.checked = Boolean(fresh?.analytics);
    marketing.checked = Boolean(fresh?.marketing);
    banner.hidden = false;
  });
}

function initMobileNav() {
  const toggle = document.querySelector('.nav-toggle');
  const nav = document.getElementById('main-nav');

  toggle.addEventListener('click', () => {
    const isOpen = nav.classList.toggle('is-open');
    toggle.setAttribute('aria-expanded', String(isOpen));
  });
}

function initYear() {
  const year = document.getElementById('year');
  year.textContent = new Date().getFullYear();
}

document.addEventListener('DOMContentLoaded', () => {
  initYear();
  initMobileNav();
  initCookieBanner();
});
