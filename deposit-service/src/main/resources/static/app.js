/* Web-клиент сервиса депозитов: список договоров, форма заключения договора, карточка договора. */

const t = Bank.t;

/* Дата окончания: как LocalDate.plusMonths — день месяца сохраняется либо прижимается к концу месяца. */
function addMonths(iso, months) {
  const [year, month, day] = iso.split('-').map(Number);
  const first = new Date(year, month - 1 + months, 1);
  const lastDay = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate();
  const pad = n => String(n).padStart(2, '0');
  return first.getFullYear() + '-' + pad(first.getMonth() + 1) + '-' + pad(Math.min(day, lastDay));
}

const app = Bank.createApp({
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
      if (!form.clientId) errors.clientId = t('common.required');
      if (!form.productId) errors.productId = t('common.required');
      if (!form.number) errors.number = t('common.required');
      else if (!/^Д-\d{6}$/.test(form.number)) errors.number = t('deposits.numberFormat');
      const amount = String(form.amount).trim().replace(',', '.');
      if (!amount) errors.amount = t('common.required');
      else if (!/^\d{1,12}(\.\d{1,2})?$/.test(amount)) errors.amount = t('contract.moneyFormat');
      else if (this.product && Number(amount) < this.product.minAmount)
        errors.amount = t('deposits.minAmount', Bank.money(this.product.minAmount), this.product.currency);
      const term = String(form.termMonths).trim();
      if (!term) errors.termMonths = t('common.required');
      else if (!/^\d{1,3}$/.test(term)) errors.termMonths = t('contract.termInteger');
      else if (this.product && (Number(term) < this.product.minTermMonths || Number(term) > this.product.maxTermMonths))
        errors.termMonths = t('contract.termRange', this.product.minTermMonths, this.product.maxTermMonths);
      return errors;
    },
    async save() {
      this.errors = this.validate();
      this.formError = Object.keys(this.errors).length ? t('common.formErrors') : '';
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
        this.notice = t('deposits.opened', created.number, created.mainAccount, created.interestAccount);
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
      if (!confirm(t('deposits.confirmWithdraw', contract.number))) return;
      try {
        await Bank.api('POST', '/api/deposits/' + contract.id + '/withdraw');
        await this.reload();
        await this.openDetails(contract.id);
        this.notice = t('deposits.withdrawn');
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
    /* Возврат к списку: сообщения карточки договора больше не нужны. */
    toList() {
      this.notice = '';
      this.view = 'list';
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
          <h1>{{ t('deposits.title') }}</h1>
          <p class="subtitle">{{ t('deposits.subtitle') }}</p>
        </div>
        <bank-day @changed="dayClosed" @failed="e => error = e.message"></bank-day>
      </div>

      <div v-if="error" id="error" class="alert error">{{ error }}</div>
      <div v-if="notice" id="notice" class="alert success">{{ notice }}</div>
      <div v-if="events.length" id="events" class="alert info">
        <b>{{ t('events.title') }}</b>
        <div v-for="(event, i) in events.slice(-8)" :key="i">{{ event }}</div>
        <div v-if="events.length > 8" class="muted">{{ t('events.more', events.length) }}</div>
      </div>

      <section v-if="view === 'list'">
        <div class="toolbar">
          <span class="muted">{{ t('contract.count', deposits.length) }}</span>
          <button id="btn-add" class="primary" @click="openForm">{{ t('contract.conclude') }}</button>
        </div>
        <div class="card">
          <table id="deposits">
            <thead>
              <tr>
                <th>{{ t('table.contract') }}</th><th>{{ t('contract.client') }}</th><th>{{ t('deposits.kind') }}</th>
                <th class="num">{{ t('contract.sum') }}</th><th class="num">{{ t('contract.rate') }}</th>
                <th>{{ t('contract.start') }}</th><th>{{ t('contract.end') }}</th><th class="num">{{ t('deposits.accrued') }}</th>
                <th class="num">{{ t('deposits.paid') }}</th><th>{{ t('contract.status') }}</th>
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
                <td><span class="badge" :class="d.status === 'ACTIVE' ? 'ok' : 'off'">{{ d.status === 'ACTIVE' ? t('contract.active') : t('deposits.closed') }}</span></td>
              </tr>
            </tbody>
          </table>
          <div v-if="!deposits.length" class="empty">{{ t('contract.none') }}</div>
        </div>
      </section>

      <section v-if="view === 'form'">
        <h2>{{ t('deposits.new') }}</h2>
        <div v-if="formError" id="form-alert" class="alert error">{{ formError }}</div>
        <form class="card" novalidate @submit.prevent="save">
          <div class="form-grid">
            <div class="field wide" :class="{ invalid: errors.clientId }">
              <label for="f-clientId">{{ t('contract.client') }} <span class="req">*</span></label>
              <select id="f-clientId" v-model="form.clientId">
                <option :value="null">{{ t('contract.chooseClient') }}</option>
                <option v-for="c in clients" :key="c.id" :value="c.id">
                  {{ c.fullName }} ({{ t('contract.passport') }} {{ c.passportSeries }} {{ c.passportNumber }})
                </option>
              </select>
              <div v-if="errors.clientId" class="error" id="e-clientId">{{ errors.clientId }}</div>
            </div>
            <div class="field" :class="{ invalid: errors.number }">
              <label for="f-number">{{ t('contract.number') }} <span class="req">*</span></label>
              <input id="f-number" type="text" v-model="form.number" v-mask="'Д-######'" placeholder="Д-000001">
              <div v-if="errors.number" class="error" id="e-number">{{ errors.number }}</div>
            </div>

            <div class="field">
              <label for="f-currency">{{ t('contract.currency') }} <span class="req">*</span></label>
              <select id="f-currency" v-model="form.currency">
                <option v-for="c in meta.currencies" :key="c.code" :value="c.code">{{ c.code }} — {{ c.name }}</option>
              </select>
            </div>
            <div class="field wide" :class="{ invalid: errors.productId }">
              <label for="f-productId">{{ t('deposits.kind') }} <span class="req">*</span></label>
              <select id="f-productId" v-model="form.productId">
                <option :value="null">{{ t('contract.chooseProduct') }}</option>
                <option v-for="p in availableProducts" :key="p.id" :value="p.id">
                  {{ t('deposits.option', p.name, p.kindTitle.toLowerCase(), p.rate) }}
                </option>
              </select>
              <div v-if="errors.productId" class="error" id="e-productId">{{ errors.productId }}</div>
              <div v-else-if="product" class="hint">{{ t('deposits.hint', product.description, money(product.minAmount),
                product.currency, product.minTermMonths, product.maxTermMonths) }}</div>
            </div>

            <div class="field" :class="{ invalid: errors.amount }">
              <label for="f-amount">{{ t('deposits.amount', form.currency) }} <span class="req">*</span></label>
              <input id="f-amount" type="text" v-model="form.amount" placeholder="0.00">
              <div v-if="errors.amount" class="error" id="e-amount">{{ errors.amount }}</div>
            </div>
            <div class="field" :class="{ invalid: errors.termMonths }">
              <label for="f-termMonths">{{ t('contract.term') }} <span class="req">*</span></label>
              <input id="f-termMonths" type="text" v-model="form.termMonths" v-mask="'###'">
              <div v-if="errors.termMonths" class="error" id="e-termMonths">{{ errors.termMonths }}</div>
            </div>
            <div class="field" :class="{ invalid: errors.rate }">
              <label for="f-rate">{{ t('deposits.rate') }}</label>
              <input id="f-rate" type="text" :value="product ? product.rate : ''" readonly>
              <div v-if="errors.rate" class="error" id="e-rate">{{ errors.rate }}</div>
            </div>

            <div class="field" :class="{ invalid: errors.startDate }">
              <label for="f-startDate">{{ t('contract.startDate') }}</label>
              <input id="f-startDate" type="text" :value="date(meta.bankDate)" readonly>
              <div v-if="errors.startDate" class="error" id="e-startDate">{{ errors.startDate }}</div>
            </div>
            <div class="field" :class="{ invalid: errors.endDate }">
              <label for="f-endDate">{{ t('contract.endDate') }}</label>
              <input id="f-endDate" type="text" :value="date(endDate)" readonly>
              <div v-if="errors.endDate" class="error" id="e-endDate">{{ errors.endDate }}</div>
            </div>
          </div>
          <div class="form-actions" style="margin-top: 18px">
            <button id="btn-save" type="submit" class="primary" :disabled="saving">{{ t('contract.conclude') }}</button>
            <button id="btn-cancel" type="button" @click="view = 'list'">{{ t('common.cancel') }}</button>
          </div>
        </form>
      </section>

      <section v-if="view === 'details' && details">
        <div class="toolbar">
          <h2>{{ t('deposits.contract', details.contract.number, details.contract.clientName) }}</h2>
          <div style="display: flex; gap: 8px">
            <button v-if="details.contract.status === 'ACTIVE' && details.contract.kind === 'REVOCABLE'"
                    id="btn-withdraw" class="danger" @click="withdraw">{{ t('deposits.withdraw') }}</button>
            <button id="btn-back" @click="toList">{{ t('common.toList') }}</button>
          </div>
        </div>
        <div class="card details">
          <div><div class="label">{{ t('deposits.kind') }}</div><div class="value">«{{ details.contract.productName }}»</div><div class="muted">{{ details.contract.kindTitle }}</div></div>
          <div><div class="label">{{ t('deposits.amountShort') }}</div><div class="value">{{ money(details.contract.amount) }} {{ details.contract.currency }}</div></div>
          <div><div class="label">{{ t('contract.rate') }}</div><div class="value">{{ details.contract.rate }} {{ t('contract.perAnnum') }}</div></div>
          <div><div class="label">{{ t('contract.status') }}</div><div class="value">{{ details.contract.status === 'ACTIVE' ? t('contract.active') : t('deposits.closedOn', date(details.contract.closedOn)) }}</div></div>
          <div><div class="label">{{ t('contract.termShort') }}</div><div class="value">{{ details.contract.termMonths }} {{ t('contract.months') }}</div></div>
          <div><div class="label">{{ t('contract.period') }}</div><div class="value">{{ date(details.contract.startDate) }} — {{ date(details.contract.endDate) }}</div></div>
          <div><div class="label">{{ t('deposits.accruedInterest') }}</div><div class="value">{{ money(details.contract.accrued) }} {{ details.contract.currency }}</div></div>
          <div><div class="label">{{ t('deposits.paidInterest') }}</div><div class="value">{{ money(details.contract.paid) }} {{ details.contract.currency }}</div></div>
        </div>

        <div class="card">
          <h2>{{ t('contract.accounts') }}</h2>
          <table id="contract-accounts">
            <thead><tr><th>{{ t('table.account') }}</th><th>{{ t('table.code') }}</th><th>{{ t('table.purpose') }}</th><th>{{ t('table.activity') }}</th>
                <th class="num">{{ t('table.debit') }}</th><th class="num">{{ t('table.credit') }}</th><th class="num">{{ t('table.balance') }}</th></tr></thead>
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
          <h2>{{ t('contract.postings') }}</h2>
          <table id="contract-operations">
            <thead><tr><th>{{ t('table.date') }}</th><th>{{ t('table.operation') }}</th><th>{{ t('table.accountShort') }}</th>
                <th class="num">{{ t('table.debit') }}</th><th class="num">{{ t('table.credit') }}</th></tr></thead>
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
}, 'deposits.page');

app.mount('#app');
