/* Web-клиент сервиса депозитов: список договоров, форма заключения договора, карточка договора. */

const REQUIRED = 'Обязательное поле';

/* Дата окончания: как LocalDate.plusMonths — день месяца сохраняется либо прижимается к концу месяца. */
function addMonths(iso, months) {
  const [year, month, day] = iso.split('-').map(Number);
  const first = new Date(year, month - 1 + months, 1);
  const lastDay = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate();
  const pad = n => String(n).padStart(2, '0');
  return first.getFullYear() + '-' + pad(first.getMonth() + 1) + '-' + pad(Math.min(day, lastDay));
}

const app = Vue.createApp({
  data() {
    return {
      view: 'list',            // list | form | details
      deposits: [],
      products: [],
      clients: [],
      meta: { bankDate: null, currencies: [], nextNumber: '' },
      form: {},
      errors: {},
      formError: '',
      details: null,
      notice: '',
      error: '',
      events: [],
      saving: false,
    };
  },
  computed: {
    availableProducts() {
      return this.products.filter(p => p.currency === this.form.currency);
    },
    product() {
      return this.products.find(p => p.id === this.form.productId) || null;
    },
    endDate() {
      const months = Number(this.form.termMonths);
      return this.meta.bankDate && months > 0 && months <= 600 ? addMonths(this.meta.bankDate, months) : null;
    },
  },
  async mounted() {
    try {
      this.products = await Bank.api('GET', '/api/products');
      await this.reload();
    } catch (e) {
      this.error = e.message;
    }
  },
  methods: {
    async reload() {
      this.deposits = await Bank.api('GET', '/api/deposits');
    },
    async openForm() {
      this.error = '';
      try {
        this.meta = await Bank.api('GET', '/api/meta');
        this.clients = await Bank.api('GET', '/api/clients');
      } catch (e) {
        this.error = e.message;
        return;
      }
      this.form = { clientId: null, currency: 'BYN', productId: null, number: this.meta.nextNumber, amount: '', termMonths: '' };
      this.errors = {};
      this.formError = '';
      this.notice = '';
      this.view = 'form';
    },
    /* Первичный контроль корректности данных договора; сервис повторяет все проверки. */
    validate() {
      const errors = {};
      const form = this.form;
      if (!form.clientId) errors.clientId = REQUIRED;
      if (!form.productId) errors.productId = REQUIRED;
      if (!form.number) errors.number = REQUIRED;
      else if (!/^Д-\d{6}$/.test(form.number)) errors.number = 'Формат номера договора: Д-000001';
      const amount = String(form.amount).trim().replace(',', '.');
      if (!amount) errors.amount = REQUIRED;
      else if (!/^\d{1,12}(\.\d{1,2})?$/.test(amount)) errors.amount = 'Денежная сумма: только цифры и не более 2 знаков после запятой';
      else if (this.product && Number(amount) < this.product.minAmount)
        errors.amount = 'Минимальная сумма вклада — ' + Bank.money(this.product.minAmount) + ' ' + this.product.currency;
      const term = String(form.termMonths).trim();
      if (!term) errors.termMonths = REQUIRED;
      else if (!/^\d{1,3}$/.test(term)) errors.termMonths = 'Срок — целое число месяцев';
      else if (this.product && (Number(term) < this.product.minTermMonths || Number(term) > this.product.maxTermMonths))
        errors.termMonths = 'Срок по программе: от ' + this.product.minTermMonths + ' до ' + this.product.maxTermMonths + ' мес.';
      return errors;
    },
    async save() {
      this.errors = this.validate();
      this.formError = Object.keys(this.errors).length ? 'Форма заполнена с ошибками — исправьте отмеченные поля' : '';
      if (this.formError) return;
      this.saving = true;
      try {
        const created = await Bank.api('POST', '/api/deposits', {
          number: this.form.number,
          productId: this.form.productId,
          currency: this.form.currency,
          clientId: this.form.clientId,
          amount: Number(String(this.form.amount).trim().replace(',', '.')),
          rate: this.product.rate,
          termMonths: Number(this.form.termMonths),
          startDate: this.meta.bankDate,
          endDate: this.endDate,
        });
        await this.reload();
        await this.openDetails(created.id);
        this.notice = 'Договор ' + created.number + ' заключён, открыты счета ' + created.mainAccount + ' и ' + created.interestAccount;
      } catch (e) {
        this.errors = e.fields;
        this.formError = e.message;
      } finally {
        this.saving = false;
      }
    },
    async openDetails(id) {
      this.notice = '';
      this.error = '';
      try {
        this.details = await Bank.api('GET', '/api/deposits/' + id);
        this.view = 'details';
      } catch (e) {
        this.error = e.message;
      }
    },
    async withdraw() {
      const contract = this.details.contract;
      if (!confirm('Отозвать вклад по договору ' + contract.number + '?')) return;
      try {
        await Bank.api('POST', '/api/deposits/' + contract.id + '/withdraw');
        await this.reload();
        await this.openDetails(contract.id);
        this.notice = 'Вклад возвращён клиенту, договор закрыт';
      } catch (e) {
        this.error = e.message;
      }
    },
    async dayClosed(result) {
      this.events = result.events;
      this.meta.bankDate = result.bankDate;
      await this.reload();
      if (this.view === 'details') await this.openDetails(this.details.contract.id);
    },
    money: Bank.money,
    date: Bank.date,
  },
  watch: {
    'form.currency'() {
      if (this.product && this.product.currency !== this.form.currency) this.form.productId = null;
    },
  },
  template: `
    <bank-nav active="deposits"></bank-nav>
    <main>
      <div class="toolbar">
        <div>
          <h1>Депозитные договоры</h1>
          <p class="subtitle">Вклады физических лиц: заключение договора, начисление и выплата процентов</p>
        </div>
        <bank-day @changed="dayClosed" @failed="e => error = e.message"></bank-day>
      </div>

      <div v-if="error" id="error" class="alert error">{{ error }}</div>
      <div v-if="notice" id="notice" class="alert success">{{ notice }}</div>
      <div v-if="events.length" id="events" class="alert info">
        <b>Протокол закрытия дня</b>
        <div v-for="(event, i) in events.slice(-8)" :key="i">{{ event }}</div>
        <div v-if="events.length > 8" class="muted">… всего записей: {{ events.length }}</div>
      </div>

      <section v-if="view === 'list'">
        <div class="toolbar">
          <span class="muted">Договоров: {{ deposits.length }}</span>
          <button id="btn-add" class="primary" @click="openForm">Заключить договор</button>
        </div>
        <div class="card">
          <table id="deposits">
            <thead>
              <tr>
                <th>Договор</th><th>Клиент</th><th>Вид депозита</th><th class="num">Сумма</th><th class="num">Ставка</th>
                <th>Начало</th><th>Окончание</th><th class="num">Начислено</th><th class="num">Выплачено</th><th>Статус</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="d in deposits" :key="d.id">
                <td class="mono"><a class="link" @click="openDetails(d.id)">{{ d.number }}</a></td>
                <td>{{ d.clientName }}</td>
                <td>{{ d.productName }}<div class="muted" style="font-size: 12.5px">{{ d.kindTitle }}</div></td>
                <td class="num">{{ money(d.amount) }} {{ d.currency }}</td>
                <td class="num">{{ d.rate }} %</td>
                <td>{{ date(d.startDate) }}</td>
                <td>{{ date(d.endDate) }}</td>
                <td class="num">{{ money(d.accrued) }}</td>
                <td class="num">{{ money(d.paid) }}</td>
                <td><span class="badge" :class="d.status === 'ACTIVE' ? 'ok' : 'off'">{{ d.status === 'ACTIVE' ? 'Действует' : 'Закрыт' }}</span></td>
              </tr>
            </tbody>
          </table>
          <div v-if="!deposits.length" class="empty">Договоров пока нет</div>
        </div>
      </section>

      <section v-if="view === 'form'">
        <h2>Новый депозитный договор</h2>
        <div v-if="formError" id="form-alert" class="alert error">{{ formError }}</div>
        <form class="card" novalidate @submit.prevent="save">
          <div class="form-grid">
            <div class="field wide" :class="{ invalid: errors.clientId }">
              <label for="f-clientId">Клиент <span class="req">*</span></label>
              <select id="f-clientId" v-model="form.clientId">
                <option :value="null">— выберите клиента —</option>
                <option v-for="c in clients" :key="c.id" :value="c.id">
                  {{ c.fullName }} (паспорт {{ c.passportSeries }} {{ c.passportNumber }})
                </option>
              </select>
              <div v-if="errors.clientId" class="error" id="e-clientId">{{ errors.clientId }}</div>
            </div>
            <div class="field" :class="{ invalid: errors.number }">
              <label for="f-number">Номер договора <span class="req">*</span></label>
              <input id="f-number" type="text" v-model="form.number" v-mask="'Д-######'" placeholder="Д-000001">
              <div v-if="errors.number" class="error" id="e-number">{{ errors.number }}</div>
            </div>

            <div class="field">
              <label for="f-currency">Валюта <span class="req">*</span></label>
              <select id="f-currency" v-model="form.currency">
                <option v-for="c in meta.currencies" :key="c.code" :value="c.code">{{ c.code }} — {{ c.name }}</option>
              </select>
            </div>
            <div class="field wide" :class="{ invalid: errors.productId }">
              <label for="f-productId">Вид депозита <span class="req">*</span></label>
              <select id="f-productId" v-model="form.productId">
                <option :value="null">— выберите программу —</option>
                <option v-for="p in availableProducts" :key="p.id" :value="p.id">
                  «{{ p.name }}» — {{ p.kindTitle.toLowerCase() }}, {{ p.rate }} % годовых
                </option>
              </select>
              <div v-if="errors.productId" class="error" id="e-productId">{{ errors.productId }}</div>
              <div v-else-if="product" class="hint">{{ product.description }}. Сумма от {{ money(product.minAmount) }} {{ product.currency }},
                срок {{ product.minTermMonths }}–{{ product.maxTermMonths }} мес.</div>
            </div>

            <div class="field" :class="{ invalid: errors.amount }">
              <label for="f-amount">Сумма вклада, {{ form.currency }} <span class="req">*</span></label>
              <input id="f-amount" type="text" v-model="form.amount" placeholder="0.00">
              <div v-if="errors.amount" class="error" id="e-amount">{{ errors.amount }}</div>
            </div>
            <div class="field" :class="{ invalid: errors.termMonths }">
              <label for="f-termMonths">Срок договора, мес. <span class="req">*</span></label>
              <input id="f-termMonths" type="text" v-model="form.termMonths" v-mask="'###'">
              <div v-if="errors.termMonths" class="error" id="e-termMonths">{{ errors.termMonths }}</div>
            </div>
            <div class="field" :class="{ invalid: errors.rate }">
              <label for="f-rate">Процент по вкладу, % годовых</label>
              <input id="f-rate" type="text" :value="product ? product.rate : ''" readonly>
              <div v-if="errors.rate" class="error" id="e-rate">{{ errors.rate }}</div>
            </div>

            <div class="field" :class="{ invalid: errors.startDate }">
              <label for="f-startDate">Дата начала</label>
              <input id="f-startDate" type="text" :value="date(meta.bankDate)" readonly>
              <div v-if="errors.startDate" class="error" id="e-startDate">{{ errors.startDate }}</div>
            </div>
            <div class="field" :class="{ invalid: errors.endDate }">
              <label for="f-endDate">Дата окончания</label>
              <input id="f-endDate" type="text" :value="date(endDate)" readonly>
              <div v-if="errors.endDate" class="error" id="e-endDate">{{ errors.endDate }}</div>
            </div>
          </div>
          <div class="form-actions" style="margin-top: 18px">
            <button id="btn-save" type="submit" class="primary" :disabled="saving">Заключить договор</button>
            <button id="btn-cancel" type="button" @click="view = 'list'">Отмена</button>
          </div>
        </form>
      </section>

      <section v-if="view === 'details' && details">
        <div class="toolbar">
          <h2>Договор {{ details.contract.number }} · {{ details.contract.clientName }}</h2>
          <div style="display: flex; gap: 8px">
            <button v-if="details.contract.status === 'ACTIVE' && details.contract.kind === 'REVOCABLE'"
                    id="btn-withdraw" class="danger" @click="withdraw">Отозвать вклад</button>
            <button id="btn-back" @click="view = 'list'">К списку</button>
          </div>
        </div>
        <div class="card details">
          <div><div class="label">Вид депозита</div><div class="value">«{{ details.contract.productName }}»</div><div class="muted">{{ details.contract.kindTitle }}</div></div>
          <div><div class="label">Сумма вклада</div><div class="value">{{ money(details.contract.amount) }} {{ details.contract.currency }}</div></div>
          <div><div class="label">Ставка</div><div class="value">{{ details.contract.rate }} % годовых</div></div>
          <div><div class="label">Статус</div><div class="value">{{ details.contract.status === 'ACTIVE' ? 'Действует' : 'Закрыт ' + date(details.contract.closedOn) }}</div></div>
          <div><div class="label">Срок</div><div class="value">{{ details.contract.termMonths }} мес.</div></div>
          <div><div class="label">Период</div><div class="value">{{ date(details.contract.startDate) }} — {{ date(details.contract.endDate) }}</div></div>
          <div><div class="label">Начислено процентов</div><div class="value">{{ money(details.contract.accrued) }} {{ details.contract.currency }}</div></div>
          <div><div class="label">Выплачено процентов</div><div class="value">{{ money(details.contract.paid) }} {{ details.contract.currency }}</div></div>
        </div>

        <div class="card">
          <h2>Счета договора</h2>
          <table id="contract-accounts">
            <thead><tr><th>Номер счёта</th><th>Код</th><th>Назначение</th><th>Активность</th><th class="num">Дебет</th><th class="num">Кредит</th><th class="num">Сальдо</th></tr></thead>
            <tbody>
              <tr v-for="a in details.accounts" :key="a.number">
                <td class="mono">{{ a.number }}</td>
                <td class="mono">{{ a.chartCode }}</td>
                <td>{{ a.chartName }}<div class="muted" style="font-size: 12.5px">{{ a.name }}</div></td>
                <td>{{ a.activityTitle }}</td>
                <td class="num">{{ money(a.debit) }}</td>
                <td class="num">{{ money(a.credit) }}</td>
                <td class="num"><b>{{ money(a.balance) }}</b></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="card">
          <h2>Проводки по договору</h2>
          <table id="contract-operations">
            <thead><tr><th>Дата</th><th>Операция</th><th>Счёт</th><th class="num">Дебет</th><th class="num">Кредит</th></tr></thead>
            <tbody>
              <template v-for="op in details.operations" :key="op.id">
                <tr v-for="(entry, i) in op.entries" :key="op.id + '-' + i">
                  <td>{{ i === 0 ? date(op.bankDate) : '' }}</td>
                  <td>{{ i === 0 ? op.description : '' }}</td>
                  <td class="mono">{{ entry.account }} <span class="muted">{{ entry.chartCode }}</span></td>
                  <td class="num">{{ entry.side === 'DEBIT' ? money(entry.amount) : '' }}</td>
                  <td class="num">{{ entry.side === 'CREDIT' ? money(entry.amount) : '' }}</td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
      </section>
    </main>`,
});

app.component('bank-nav', Bank.navComponent);
app.component('bank-day', Bank.dayComponent);
app.directive('mask', Bank.maskDirective);
app.mount('#app');
