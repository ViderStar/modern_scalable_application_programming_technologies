/* Web-клиент модуля «Работа с клиентами»: форма «Список клиентов» и форма добавления/редактирования. */

const REQUIRED = 'Обязательное поле';
const NAME = /^[А-Яа-яЁёA-Za-z]+(?:[-' ][А-Яа-яЁёA-Za-z]+)*$/;
const letters = v => NAME.test(v) || 'Допустимы только буквы';

function pastDate(v) {
  const date = Bank.parseDate(v);
  if (!date) return 'Такой даты не существует, формат ДД.ММ.ГГГГ';
  if (date > new Date()) return 'Дата не может быть в будущем';
  if (date.getFullYear() < 1900) return 'Дата не может быть раньше 1900 года';
  return true;
}

/*
 * Описание полей формы — единственное место, где заданы обязательность, маска ввода и проверка значения.
 * Первичная (клиентская) валидация: required — обязательность, mask — маска ввода, check — проверка формата.
 * Те же правила повторно проверяет сервис (ClientRequest + Masks).
 */
const SECTIONS = [
  { title: 'Личные данные', fields: [
    { name: 'lastName',   label: 'Фамилия',  required: true, max: 60, check: letters },
    { name: 'firstName',  label: 'Имя',      required: true, max: 60, check: letters },
    { name: 'middleName', label: 'Отчество', required: true, max: 60, check: letters },
    { name: 'birthDate',  label: 'Дата рождения', required: true, mask: '##.##.####', placeholder: 'ДД.ММ.ГГГГ', check: pastDate },
    { name: 'sex',        label: 'Пол', required: true, type: 'radio', options: [{ id: 'M', name: 'Мужской' }, { id: 'F', name: 'Женский' }] },
    { name: 'birthPlace', label: 'Место рождения', required: true, max: 200 },
  ]},
  { title: 'Паспортные данные', fields: [
    { name: 'passportSeries', label: 'Серия паспорта', required: true, mask: 'AA', placeholder: 'MP',
      check: v => /^[A-Z]{2}$/.test(v) || 'Серия — две заглавные латинские буквы' },
    { name: 'passportNumber', label: '№ паспорта', required: true, mask: '#######', placeholder: '1234567',
      check: v => /^\d{7}$/.test(v) || 'Номер паспорта — семь цифр' },
    { name: 'identificationNumber', label: 'Идентификационный номер', required: true, mask: '#######A###AA#', placeholder: '3140301A001PB5',
      check: v => /^\d{7}[A-Z]\d{3}[A-Z]{2}\d$/.test(v) || 'Формат: 7 цифр, буква, 3 цифры, 2 буквы, цифра' },
    { name: 'issuedBy',  label: 'Кем выдан', required: true, max: 200, wide: true },
    { name: 'issueDate', label: 'Дата выдачи', required: true, mask: '##.##.####', placeholder: 'ДД.ММ.ГГГГ',
      check: (v, form) => {
        const valid = pastDate(v);
        if (valid !== true) return valid;
        const birth = Bank.parseDate(form.birthDate);
        return !birth || Bank.parseDate(v) >= birth || 'Паспорт не может быть выдан раньше даты рождения';
      } },
  ]},
  { title: 'Проживание и контакты', fields: [
    { name: 'residenceCityId',    label: 'Город фактического проживания', required: true, type: 'select', dictionary: 'cities' },
    { name: 'residenceAddress',   label: 'Адрес фактического проживания', required: true, max: 200, wide: true },
    { name: 'registrationCityId', label: 'Город прописки', required: true, type: 'select', dictionary: 'cities' },
    { name: 'homePhone',   label: 'Телефон домашний', mask: '###-##-##', placeholder: '293-88-44',
      check: v => /^\d{3}-\d{2}-\d{2}$/.test(v) || 'Формат: 293-88-44' },
    { name: 'mobilePhone', label: 'Телефон мобильный', mask: '+375 (##) ###-##-##', placeholder: '+375 (29) 314-15-92',
      check: v => /^\+375 \((25|29|33|44)\) \d{3}-\d{2}-\d{2}$/.test(v) || 'Формат: +375 (29) 314-15-92, код 25, 29, 33 или 44' },
    { name: 'email', label: 'E-mail', max: 100, placeholder: 'name@example.by',
      check: v => /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(v) || 'Некорректный e-mail' },
  ]},
  { title: 'Социальный статус', fields: [
    { name: 'maritalStatusId', label: 'Семейное положение', required: true, type: 'select', dictionary: 'maritalStatuses' },
    { name: 'citizenshipId',   label: 'Гражданство',        required: true, type: 'select', dictionary: 'citizenships' },
    { name: 'disabilityId',    label: 'Инвалидность',       required: true, type: 'select', dictionary: 'disabilities' },
    { name: 'monthlyIncome',   label: 'Ежемесячный доход, BYN', placeholder: '0.00',
      check: v => /^\d{1,12}([.,]\d{1,2})?$/.test(v) || 'Денежная сумма: только цифры и не более 2 знаков после запятой' },
    { name: 'pensioner', label: 'Пенсионер', type: 'checkbox' },
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
      if (field.required) errors[field.name] = REQUIRED;
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

const app = Vue.createApp({
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
      this.formError = Object.keys(this.errors).length ? 'Форма заполнена с ошибками — исправьте отмеченные поля' : '';
      if (this.formError) return window.scrollTo(0, 0);
      this.saving = true;
      try {
        const payload = toPayload(this.form);
        if (this.editingId === null) await Bank.api('POST', '/api/clients', payload);
        else await Bank.api('PUT', '/api/clients/' + this.editingId, payload);
        await this.reload();
        this.notice = this.editingId === null ? 'Клиент добавлен' : 'Изменения сохранены';
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
      if (!confirm('Удалить клиента ' + client.fullName + '?')) return;
      try {
        await Bank.api('DELETE', '/api/clients/' + client.id);
        await this.reload();
        this.notice = 'Клиент удалён';
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
            <h1>Список клиентов</h1>
            <p class="subtitle">Всего клиентов: <span id="clients-count">{{ clients.length }}</span>. Сортировка по фамилии.</p>
          </div>
          <button id="btn-add" class="primary" @click="openCreate">Добавить клиента</button>
        </div>
        <div v-if="notice" id="notice" class="alert success">{{ notice }}</div>
        <div class="card">
          <table id="clients">
            <thead>
              <tr>
                <th>ФИО</th><th>Дата рождения</th><th>Паспорт</th><th>Идент. номер</th>
                <th>Город проживания</th><th>Мобильный телефон</th><th class="num">Доход, BYN</th><th></th>
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
                  <button class="small edit" @click="openEdit(client.id)">Изменить</button>
                  <button class="small danger delete" @click="remove(client)">Удалить</button>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="!clients.length" class="empty">Клиентов пока нет</div>
        </div>
      </section>

      <section v-else>
        <h1>{{ editingId === null ? 'Новый клиент' : 'Редактирование клиента' }}</h1>
        <p class="subtitle">Поля, отмеченные <span style="color: var(--bad)">*</span>, обязательны для заполнения.</p>
        <div v-if="formError" id="form-alert" class="alert error">{{ formError }}</div>
        <form class="card" novalidate @submit.prevent="save">
          <fieldset v-for="section in sections" :key="section.title">
            <legend>{{ section.title }}</legend>
            <div class="form-grid">
              <div v-for="field in section.fields" :key="field.name"
                   class="field" :class="{ invalid: errors[field.name], wide: field.wide }">
                <label :for="'f-' + field.name">{{ field.label }} <span v-if="field.required" class="req">*</span></label>

                <select v-if="field.type === 'select'" :id="'f-' + field.name" v-model="form[field.name]">
                  <option :value="null">— выберите —</option>
                  <option v-for="item in options(field)" :key="item.id" :value="item.id">{{ item.name }}</option>
                </select>

                <div v-else-if="field.type === 'radio'" class="choices">
                  <label v-for="item in options(field)" :key="item.id">
                    <input type="radio" :id="'f-' + field.name + '-' + item.id" :name="field.name"
                           :value="item.id" v-model="form[field.name]"> {{ item.name }}
                  </label>
                </div>

                <div v-else-if="field.type === 'checkbox'" class="choices">
                  <label><input type="checkbox" :id="'f-' + field.name" v-model="form[field.name]"> да</label>
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
            <button id="btn-save" type="submit" class="primary" :disabled="saving">Сохранить</button>
            <button id="btn-cancel" type="button" @click="view = 'list'">Отмена</button>
            <span style="flex: 1"></span>
            <button v-if="editingId !== null" id="btn-delete" type="button" class="danger"
                    @click="remove({ id: editingId, fullName: form.lastName + ' ' + form.firstName })">Удалить клиента</button>
          </div>
        </form>
      </section>
    </main>`,
});

app.component('bank-nav', Bank.navComponent);
app.directive('mask', Bank.maskDirective);
app.mount('#app');
