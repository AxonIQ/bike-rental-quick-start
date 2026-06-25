import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import Database from 'better-sqlite3';
import { BikeStatus, RentalStatus } from '@bikerental/core-api';

interface BikeStatusRow {
  bikeId: string;
  bikeType: string;
  location: string;
  renter: string | null;
  status: RentalStatus;
}

/**
 * The bike read-model store, backed by an embedded SQLite database (the Node equivalent of the Java
 * service's file-backed H2). Replaces the Spring Data `JpaRepository<BikeStatus, String>`; the derived
 * query methods are reimplemented as SQL. The table columns match the entity's fields, so a row maps
 * straight onto a {@link BikeStatus} instance.
 */
export class BikeStatusRepository {
  private readonly db: Database.Database;

  constructor(file: string) {
    mkdirSync(dirname(file), { recursive: true });
    this.db = new Database(file);
    this.db.pragma('journal_mode = WAL');
    this.db.exec(`CREATE TABLE IF NOT EXISTS bike_status (
      bikeId   TEXT PRIMARY KEY,
      bikeType TEXT,
      location TEXT,
      renter   TEXT,
      status   TEXT
    )`);
  }

  findAll(): BikeStatus[] {
    return this.rows('SELECT * FROM bike_status').map(toEntity);
  }

  findById(bikeId: string): BikeStatus | undefined {
    const row = this.db.prepare('SELECT * FROM bike_status WHERE bikeId = ?').get(bikeId) as
      | BikeStatusRow
      | undefined;
    return row ? toEntity(row) : undefined;
  }

  save(status: BikeStatus): BikeStatus {
    this.db
      .prepare(
        `INSERT INTO bike_status (bikeId, bikeType, location, renter, status)
         VALUES (@bikeId, @bikeType, @location, @renter, @status)
         ON CONFLICT(bikeId) DO UPDATE SET
           bikeType = excluded.bikeType,
           location = excluded.location,
           renter   = excluded.renter,
           status   = excluded.status`,
      )
      .run({
        bikeId: status.bikeId,
        bikeType: status.bikeType,
        location: status.location,
        renter: status.renter ?? null,
        status: status.status,
      });
    return status;
  }

  findAllByBikeTypeAndStatus(bikeType: string, status: RentalStatus): BikeStatus[] {
    return this.rows('SELECT * FROM bike_status WHERE bikeType = ? AND status = ?', bikeType, status).map(
      toEntity,
    );
  }

  countBikeStatusesByBikeType(bikeType: string): number {
    const row = this.db
      .prepare('SELECT COUNT(*) AS count FROM bike_status WHERE bikeType = ?')
      .get(bikeType) as { count: number };
    return row.count;
  }

  private rows(sql: string, ...params: unknown[]): BikeStatusRow[] {
    return this.db.prepare(sql).all(...params) as BikeStatusRow[];
  }
}

function toEntity(row: BikeStatusRow): BikeStatus {
  return Object.assign(Object.create(BikeStatus.prototype) as BikeStatus, row);
}
