/* Общие помощники web-клиентов банка: язык интерфейса, REST-вызовы, маски ввода, форматирование. */
const Bank = {

  /* Адреса web-клиентов остальных микросервисов для общей навигации. */
  services: [
    { key: 'clients',  port: 8081 },
    { key: 'accounts', port: 8082 },
    { key: 'deposits', port: 8083 },
    { key: 'credits',  port: 8084 },
    { key: 'atm',      port: 8085 },
  ],

  /* ---------- язык интерфейса ---------- */

  languages: [
    { code: 'ru', title: 'Рус', locale: 'ru-RU' },
    { code: 'en', title: 'Eng', locale: 'en-GB' },
    { code: 'be', title: 'Бел', locale: 'be-BY' },
  ],

  /* Выбор хранится в cookie: она общая для всех портов localhost, поэтому язык един для всех пяти web-клиентов. */
  lang: (document.cookie.match(/(?:^|; )bank_lang=(ru|en|be)/) || [])[1] || 'ru',

  /* Словари: общая часть — в ui-kit/i18n.js, тексты приложения — в его собственном i18n.js. */
  messages: { ru: {}, en: {}, be: {} },

  addMessages(dictionary) {
    for (const lang of Object.keys(dictionary)) Object.assign(Bank.messages[lang], dictionary[lang]);
  },

  /* Текст по ключу на выбранном языке; {0}, {1} заменяются аргументами. Непереведённый ключ берётся из русского словаря. */
  t(key, ...args) {
    const text = Bank.messages[Bank.lang][key] ?? Bank.messages.ru[key] ?? key;
    return text.replace(/\{(\d+)\}/g, (match, index) => args[index] ?? '');
  },

  setLang(code) {
    document.cookie = 'bank_lang=' + code + '; path=/; max-age=31536000; SameSite=Lax';
    location.reload();
  },

  locale() {
    return Bank.languages.find(language => language.code === Bank.lang).locale;
  },

  /* ---------- REST ---------- */

  /* REST-вызов: язык передаётся сервису заголовком Accept-Language; при ошибке бросает { status, message, fields }. */
  async api(method, url, body) {
    const options = { method, headers: { 'Accept': 'application/json', 'Accept-Language': Bank.lang } };
    if (body !== undefined) {
      options.headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(body);
    }
    let response;
    try {
      response = await fetch(url, options);
    } catch (e) {
      throw { status: 0, message: Bank.t('common.unavailable'), fields: {} };
    }
    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) {
      throw {
        status: response.status,
        message: (data && data.message) || Bank.t('common.error', response.status),
        fields: (data && data.fields) || {},
      };
    }
    return data;
  },

  /* ---------- ввод и форматирование ---------- */

  /* Маска ввода: # — цифра, A — латинская буква (в верхнем регистре), остальное — литералы. */
  mask(value, pattern) {
    const raw = String(value ?? '');
    let out = '';
    let i = 0;
    for (const p of pattern) {
      if (i >= raw.length) break;
      if (p === '#' || p === 'A') {
        const allowed = p === '#' ? /\d/ : /[a-zA-Z]/;
        while (i < raw.length && !allowed.test(raw[i])) i++;
        if (i >= raw.length) break;
        out += raw[i++].toUpperCase();
      } else {
        out += p;
        if (raw[i] === p) i++;
      }
    }
    return out;
  },

  /* Директива v-mask="'##.##.####'" — применяет маску при каждом вводе. */
  maskDirective: {
    mounted(el, binding) {
      el.addEventListener('input', () => {
        const masked = Bank.mask(el.value, binding.value);
        if (masked !== el.value) {
          el.value = masked;
          el.dispatchEvent(new Event('input'));
        }
      });
    },
  },

  /* Строгий разбор даты ДД.ММ.ГГГГ: 31.02.2015 и 29.02.2015 датами не считаются. */
  parseDate(text) {
    const m = /^(\d{2})\.(\d{2})\.(\d{4})$/.exec(text || '');
    if (!m) return null;
    const [day, month, year] = [Number(m[1]), Number(m[2]), Number(m[3])];
    const date = new Date(year, month - 1, day);
    const same = date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
    return same ? date : null;
  },

  money(value) {
    if (value === null || value === undefined || value === '') return '';
    return Number(value).toLocaleString(Bank.locale(), { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  },

  /* ISO-дата 2026-10-01 -> 01.10.2026 */
  date(iso) {
    return iso ? String(iso).split('-').reverse().join('.') : '';
  },

  /* ---------- общие компоненты ---------- */

  /* Создание приложения Vue: функция t доступна в шаблонах, подключены шапка, панель дня и маски. */
  createApp(options, pageTitleKey) {
    document.documentElement.lang = Bank.lang;
    document.title = Bank.t(pageTitleKey) + ' — BankEt';
    const app = Vue.createApp(options);
    app.config.globalProperties.t = Bank.t;
    app.component('bank-nav', Bank.navComponent);
    app.component('bank-day', Bank.dayComponent);
    app.directive('mask', Bank.maskDirective);
    return app;
  },

  /* Панель банковского дня: текущая дата и процедура «Закрытие банковского дня». */
  dayComponent: {
    emits: ['changed', 'failed'],
    data() {
      return { date: null, days: 30, busy: false };
    },
    async mounted() {
      try {
        this.date = (await Bank.api('GET', '/api/bank-day')).date;
      } catch (e) {
        this.$emit('failed', e);
      }
    },
    methods: {
      async close(days) {
        this.busy = true;
        try {
          const result = await Bank.api('POST', '/api/bank-day/close?days=' + days);
          this.date = result.bankDate;
          this.$emit('changed', result);
        } catch (e) {
          this.$emit('failed', e);
        } finally {
          this.busy = false;
        }
      },
      format: iso => Bank.date(iso),
    },
    template: `
      <div class="bankday">
        <span class="label">{{ t('day.label') }}</span>
        <b id="bank-date">{{ format(date) }}</b>
        <button id="btn-close-day" class="primary" :disabled="busy" @click="close(1)">{{ t('day.close') }}</button>
        <input id="days" type="text" v-model.number="days" maxlength="4">
        <button id="btn-close-days" :disabled="busy || !(days > 0)" @click="close(days)">{{ t('day.closeDays') }}</button>
      </div>`,
  },

  /* Шапка: переходы между web-клиентами микросервисов и выбор языка. */
  navComponent: {
    props: ['active'],
    data() {
      return {
        services: Bank.services,
        languages: Bank.languages,
        lang: Bank.lang,
        host: location.protocol + '//' + location.hostname,
      };
    },
    methods: {
      setLang: code => Bank.setLang(code),
    },
    template: `
      <header class="topbar">
        <div class="brand">BankEt <span>{{ t('brand.subtitle') }}</span></div>
        <nav>
          <a v-for="s in services" :key="s.key" :href="host + ':' + s.port + '/'"
             :class="{ active: s.key === active }">{{ t('nav.' + s.key) }}</a>
        </nav>
        <div class="languages">
          <button v-for="l in languages" :key="l.code" :id="'lang-' + l.code"
                  :class="{ active: l.code === lang }" @click="setLang(l.code)">{{ l.title }}</button>
        </div>
      </header>`,
  },
};
