import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import Database from 'better-sqlite3';
import { PaymentStatus, PaymentStatusValue } from '@bikerental/core-api';

interface PaymentStatusRow {
  id: string;
  status: PaymentStatusValue;
  amount: number;
  reference: string | null;
}

/**
 * The payment read-model store, backed by an embedded SQLite database (the Node equivalent of the Java
 * service's file-backed H2). Replaces the Spring Data `CrudRepository<PaymentStatus, String>`; the
 * derived query methods are reimplemented as SQL.
 */
export class PaymentStatusRepository {
  private readonly db: Database.Database;

  constructor(file: string) {
    mkdirSync(dirname(file), { recursive: true });
    this.db = new Database(file);
    this.db.pragma('journal_mode = WAL');
    this.db.exec(`CREATE TABLE IF NOT EXISTS payment_status (
      id        TEXT PRIMARY KEY,
      status    TEXT,
      amount    INTEGER,
      reference TEXT
    )`);
  }

  findAll(): PaymentStatus[] {
    return this.rows('SELECT * FROM payment_status').map(toEntity);
  }

  findById(id: string): PaymentStatus | undefined {
    const row = this.db.prepare('SELECT * FROM payment_status WHERE id = ?').get(id) as
      | PaymentStatusRow
      | undefined;
    return row ? toEntity(row) : undefined;
  }

  save(payment: PaymentStatus): PaymentStatus {
    this.db
      .prepare(
        `INSERT INTO payment_status (id, status, amount, reference)
         VALUES (@id, @status, @amount, @reference)
         ON CONFLICT(id) DO UPDATE SET
           status    = excluded.status,
           amount    = excluded.amount,
           reference = excluded.reference`,
      )
      .run({
        id: payment.id,
        status: payment.status,
        amount: payment.amount,
        reference: payment.reference ?? null,
      });
    return payment;
  }

  findAllByStatus(status: PaymentStatusValue): PaymentStatus[] {
    return this.rows('SELECT * FROM payment_status WHERE status = ?', status).map(toEntity);
  }

  findByReferenceAndStatus(reference: string, status: PaymentStatusValue): PaymentStatus | undefined {
    const row = this.db
      .prepare('SELECT * FROM payment_status WHERE reference = ? AND status = ?')
      .get(reference, status) as PaymentStatusRow | undefined;
    return row ? toEntity(row) : undefined;
  }

  private rows(sql: string, ...params: unknown[]): PaymentStatusRow[] {
    return this.db.prepare(sql).all(...params) as PaymentStatusRow[];
  }
}

function toEntity(row: PaymentStatusRow): PaymentStatus {
  return Object.assign(Object.create(PaymentStatus.prototype) as PaymentStatus, row);
}
