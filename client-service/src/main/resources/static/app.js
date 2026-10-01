/* Web-клиент модуля «Работа с клиентами»: форма «Список клиентов» и форма добавления/редактирования. */

const t = Bank.t;
const NAME = /^[А-Яа-яЁёA-Za-z]+(?:[-' ][А-Яа-яЁёA-Za-z]+)*$/;
const letters = v => NAME.test(v) || t('check.letters');

function pastDate(v) {
  const date = Bank.parseDate(v);
  if (!date) return t('check.dateInvalid');
  if (date > new Date()) return t('check.dateFuture');
  if (date.getFullYear() < 1900) return t('check.dateOld');
  return true;
}

/*
 * Описание полей формы — единственное место, где заданы обязательность, маска ввода и проверка значения.
 * Первичная (клиентская) валидация: required — обязательность, mask — маска ввода, check — проверка формата.
 * Те же правила повторно проверяет сервис (ClientRequest + Masks).
 * Подписи и сообщения берутся из словаря i18n.js по ключам field.<имя>, section.<раздел>, check.<правило>.
 */
const SECTIONS = [
  { title: 'personal', fields: [
    { name: 'lastName', required: true, max: 60, check: letters },
    { name: 'firstName', required: true, max: 60, check: letters },
    { name: 'middleName', required: true, max: 60, check: letters },
    { name: 'birthDate', required: true, mask: '##.##.####', placeholder: t('placeholder.date'), check: pastDate },
    { name: 'sex', required: true, type: 'radio', options: [{ id: 'M', name: t('sex.M') }, { id: 'F', name: t('sex.F') }] },
    { name: 'birthPlace', required: true, max: 200 },
  ]},
  { title: 'passport', fields: [
    { name: 'passportSeries', required: true, mask: 'AA', placeholder: 'MP',
      check: v => /^[A-Z]{2}$/.test(v) || t('check.passportSeries') },
    { name: 'passportNumber', required: true, mask: '#######', placeholder: '1234567',
      check: v => /^\d{7}$/.test(v) || t('check.passportNumber') },
    { name: 'identificationNumber', required: true, mask: '#######A###AA#', placeholder: '3140301A001PB5',
      check: v => /^\d{7}[A-Z]\d{3}[A-Z]{2}\d$/.test(v) || t('check.identificationNumber') },
    { name: 'issuedBy', required: true, max: 200, wide: true },
    { name: 'issueDate', required: true, mask: '##.##.####', placeholder: t('placeholder.date'),
      check: (v, form) => {
        const valid = pastDate(v);
        if (valid !== true) return valid;
        const birth = Bank.parseDate(form.birthDate);
        return !birth || Bank.parseDate(v) >= birth || t('check.issueBeforeBirth');
      } },
  ]},
  { title: 'contacts', fields: [
    { name: 'residenceCityId', required: true, type: 'select', dictionary: 'cities' },
    { name: 'residenceAddress', required: true, max: 200, wide: true },
    { name: 'registrationCityId', required: true, type: 'select', dictionary: 'cities' },
    { name: 'homePhone', mask: '###-##-##', placeholder: '293-88-44',
      check: v => /^\d{3}-\d{2}-\d{2}$/.test(v) || t('check.homePhone') },
    { name: 'mobilePhone', mask: '+375 (##) ###-##-##', placeholder: '+375 (29) 314-15-92',
      check: v => /^\+375 \((25|29|33|44)\) \d{3}-\d{2}-\d{2}$/.test(v) || t('check.mobilePhone') },
    { name: 'email', max: 100, placeholder: 'name@example.by',
      check: v => /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(v) || t('check.email') },
  ]},
  { title: 'social', fields: [
    { name: 'maritalStatusId', required: true, type: 'select', dictionary: 'maritalStatuses' },
    { name: 'citizenshipId', required: true, type: 'select', dictionary: 'citizenships' },
    { name: 'disabilityId', required: true, type: 'select', dictionary: 'disabilities' },
    { name: 'monthlyIncome', placeholder: '0.00',
      check: v => /^\d{1,12}([.,]\d{1,2})?$/.test(v) || t('check.income') },
    { name: 'pensioner', type: 'checkbox' },
  ]},
];
const FIELDS = SECTIONS.flatMap(section => section.fields);

/* Проверка формы перед отправкой: возвращает { имяПоля: сообщение }. Пустой объект — ошибок нет. */
function validate(form) {
  const errors = {};
  for (const field of FIELDS) {
    const raw = form[field.name];
    const value = typeof raw === 'string' ? raw.trim() : raw;
    const empty = value === null || value === undefined || value === '';
    if (empty) {
      if (field.required) errors[field.name] = t('common.required');
      continue;
    }
    const result = field.check ? field.check(value, form) : true;
    if (result !== true) errors[field.name] = result;
  }
  return errors;
}

function emptyForm() {
  const form = {};
  for (const field of FIELDS) form[field.name] = field.type === 'checkbox' ? false : (field.type === 'select' ? null : '');
  return form;
}

/* Тело REST-запроса: строки без пробелов по краям, пустые необязательные поля — null, доход — число. */
function toPayload(form) {
  const payload = {};
  for (const field of FIELDS) {
    const raw = form[field.name];
    const value = typeof raw === 'string' ? raw.trim() : raw;
    payload[field.name] = value === '' ? null : value;
  }
  if (payload.monthlyIncome !== null) payload.monthlyIncome = Number(String(payload.monthlyIncome).replace(',', '.'));
  return payload;
}

const app = Bank.createApp({
  data() {
    return {
      view: 'list',            // list | form
      clients: [],
      dictionaries: { cities: [], maritalStatuses: [], citizenships: [], disabilities: [] },
      sections: SECTIONS,
      form: emptyForm(),
      editingId: null,
      errors: {},
      formError: '',
      notice: '',
      loadError: '',
      saving: false,
    };
  },
  async mounted() {
    try {
      this.dictionaries = await Bank.api('GET', '/api/dictionaries');
      await this.reload();
    } catch (e) {
      this.loadError = e.message;
    }
  },
  methods: {
    async reload() {
      this.clients = await Bank.api('GET', '/api/clients');
    },
    openCreate() {
      this.form = emptyForm();
      this.editingId = null;
      this.showForm();
    },
    async openEdit(id) {
      const client = await Bank.api('GET', '/api/clients/' + id);
      const form = emptyForm();
      for (const field of FIELDS) form[field.name] = client[field.name] ?? form[field.name];
      if (client.monthlyIncome !== null) form.monthlyIncome = Number(client.monthlyIncome).toFixed(2);
      this.form = form;
      this.editingId = id;
      this.showForm();
    },
    showForm() {
      this.errors = {};
      this.formError = '';
      this.notice = '';
      this.view = 'form';
    },
    async save() {
      this.errors = validate(this.form);
      this.formError = Object.keys(this.errors).length ? t('common.formErrors') : '';
      if (this.formError) return window.scrollTo(0, 0);
      this.saving = true;
      try {
        const payload = toPayload(this.form);
        if (this.editingId === null) await Bank.api('POST', '/api/clients', payload);
        else await Bank.api('PUT', '/api/clients/' + this.editingId, payload);
        await this.reload();
        this.notice = this.editingId === null ? t('clients.added') : t('clients.saved');
        this.view = 'list';
      } catch (e) {
        this.errors = e.fields;        // сообщения серверной валидации показываются у тех же полей
        this.formError = e.message;
        window.scrollTo(0, 0);
      } finally {
        this.saving = false;
      }
    },
    async remove(client) {
      if (!confirm(t('clients.confirmDelete', client.fullName))) return;
      try {
        await Bank.api('DELETE', '/api/clients/' + client.id);
        await this.reload();
        this.notice = t('clients.deleted');
        this.view = 'list';
      } catch (e) {
        this.notice = '';
        this.loadError = e.message;
      }
    },
    options(field) {
      return field.options || this.dictionaries[field.dictionary];
    },
    money: Bank.money,
  },
  template: `
    <bank-nav active="clients"></bank-nav>
    <main>
      <div v-if="loadError" class="alert error">{{ loadError }}</div>

      <section v-if="view === 'list'">
        <div class="toolbar">
          <div>
            <h1>{{ t('clients.list') }}</h1>
            <p class="subtitle" id="clients-count">{{ t('clients.count', clients.length) }}</p>
          </div>
          <button id="btn-add" class="primary" @click="openCreate">{{ t('clients.add') }}</button>
        </div>
        <div v-if="notice" id="notice" class="alert success">{{ notice }}</div>
        <div class="card">
          <table id="clients">
            <thead>
              <tr>
                <th>{{ t('col.fio') }}</th><th>{{ t('col.birthDate') }}</th><th>{{ t('col.passport') }}</th>
                <th>{{ t('col.identification') }}</th><th>{{ t('col.city') }}</th><th>{{ t('col.mobile') }}</th>
                <th class="num">{{ t('col.income') }}</th><th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="client in clients" :key="client.id">
                <td class="fio">{{ client.fullName }}</td>
                <td class="nowrap">{{ client.birthDate }}</td>
                <td class="mono">{{ client.passportSeries }} {{ client.passportNumber }}</td>
                <td class="mono">{{ client.identificationNumber }}</td>
                <td>{{ client.residenceCity }}</td>
                <td class="nowrap">{{ client.mobilePhone }}</td>
                <td class="num">{{ money(client.monthlyIncome) }}</td>
                <td class="actions">
                  <button class="small edit" @click="openEdit(client.id)">{{ t('clients.edit') }}</button>
                  <button class="small danger delete" @click="remove(client)">{{ t('clients.delete') }}</button>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="!clients.length" class="empty">{{ t('clients.empty') }}</div>
        </div>
      </section>

      <section v-else>
        <h1>{{ editingId === null ? t('clients.new') : t('clients.editing') }}</h1>
        <p class="subtitle">{{ t('clients.requiredHint') }}</p>
        <div v-if="formError" id="form-alert" class="alert error">{{ formError }}</div>
        <form class="card" novalidate @submit.prevent="save">
          <fieldset v-for="section in sections" :key="section.title">
            <legend>{{ t('section.' + section.title) }}</legend>
            <div class="form-grid">
              <div v-for="field in section.fields" :key="field.name"
                   class="field" :class="{ invalid: errors[field.name], wide: field.wide }">
                <label :for="'f-' + field.name">{{ t('field.' + field.name) }} <span v-if="field.required" class="req">*</span></label>

                <select v-if="field.type === 'select'" :id="'f-' + field.name" v-model="form[field.name]">
                  <option :value="null">{{ t('clients.choose') }}</option>
                  <option v-for="item in options(field)" :key="item.id" :value="item.id">{{ item.name }}</option>
                </select>

                <div v-else-if="field.type === 'radio'" class="choices">
                  <label v-for="item in options(field)" :key="item.id">
                    <input type="radio" :id="'f-' + field.name + '-' + item.id" :name="field.name"
                           :value="item.id" v-model="form[field.name]"> {{ item.name }}
                  </label>
                </div>

                <div v-else-if="field.type === 'checkbox'" class="choices">
                  <label><input type="checkbox" :id="'f-' + field.name" v-model="form[field.name]"> {{ t('clients.yes') }}</label>
                </div>

                <input v-else-if="field.mask" type="text" :id="'f-' + field.name" v-model="form[field.name]"
                       v-mask="field.mask" :placeholder="field.placeholder" autocomplete="off">

                <input v-else type="text" :id="'f-' + field.name" v-model="form[field.name]"
                       :maxlength="field.max" :placeholder="field.placeholder" autocomplete="off">

                <div v-if="errors[field.name]" class="error" :id="'e-' + field.name">{{ errors[field.name] }}</div>
              </div>
            </div>
          </fieldset>
          <div class="form-actions">
            <button id="btn-save" type="submit" class="primary" :disabled="saving">{{ t('clients.save') }}</button>
            <button id="btn-cancel" type="button" @click="view = 'list'">{{ t('common.cancel') }}</button>
            <span style="flex: 1"></span>
            <button v-if="editingId !== null" id="btn-delete" type="button" class="danger"
                    @click="remove({ id: editingId, fullName: form.lastName + ' ' + form.firstName })">{{ t('clients.deleteClient') }}</button>
          </div>
        </form>
      </section>
    </main>`,
}, 'clients.page');

app.mount('#app');
