/** Payment lifecycle status. String-valued so its JSON form matches the Java enum name. */
export enum PaymentStatusValue {
  PENDING = 'PENDING',
  APPROVED = 'APPROVED',
  REJECTED = 'REJECTED',
}

/**
 * The payment read-model entity. Formerly a JPA `@Entity`; here a plain object persisted by the
 * {@link PaymentStatusRepository}. Its JSON form (see {@link toJSON}) carries `id`, `amount` and
 * `status` — the `reference` is internal, matching the Java entity, which had no `getReference`.
 */
export class PaymentStatus {
  static readonly TYPE = 'io.axoniq.demo.bikerental.coreapi.payment.PaymentStatus';

  id!: string;
  status!: PaymentStatusValue;
  amount!: number;
  reference?: string;

  constructor(id?: string, amount?: number, reference?: string) {
    if (id !== undefined) {
      this.id = id;
      this.amount = amount!;
      this.reference = reference;
      this.status = PaymentStatusValue.PENDING;
    }
  }

  getId(): string {
    return this.id;
  }

  getStatus(): PaymentStatusValue {
    return this.status;
  }

  getAmount(): number {
    return this.amount;
  }

  setStatus(status: PaymentStatusValue): void {
    this.status = status;
  }

  toJSON(): { id: string; amount: number; status: PaymentStatusValue } {
    return { id: this.id, amount: this.amount, status: this.status };
  }
}
