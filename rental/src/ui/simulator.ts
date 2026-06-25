import { randomUUID } from 'node:crypto';
import {
  CommandDispatcher,
  CountOfBikesByTypeQuery,
  createLogger,
  JavaTypes,
  QueryDispatcher,
  RegisterBikeCommand,
} from '@bikerental/core-api';
import type { config } from '../config.js';
import { BikeRentalDataGenerator } from './bike-rental-data-generator.js';

const logger = createLogger('Simulator');

const FIXED_RATE_MS = 25_000;
const INITIAL_DELAY_MS = 5_000;

/**
 * The 'simulator' profile component: every 25s it tops up the bike inventory and kicks off a batch of
 * rental cycles. Active only when the simulator profile is enabled (see {@link config}).
 */
export class Simulator {
  private inventorySize: number;
  private inventoryBikeType: string;
  private rentalBikeType: string;
  private loops: number;
  private concurrency: number;
  private abandonPaymentFactor: number;
  private delayBetweenLoops: number;
  private timer?: NodeJS.Timeout;

  constructor(
    private readonly commandDispatcher: CommandDispatcher,
    private readonly queryDispatcher: QueryDispatcher,
    private readonly dataGenerator: BikeRentalDataGenerator,
    settings: (typeof config)['simulator'],
  ) {
    this.inventorySize = settings.inventorySize;
    this.inventoryBikeType = settings.inventoryBikeType;
    this.rentalBikeType = settings.rentalBikeType;
    this.loops = settings.loops;
    this.concurrency = settings.concurrency;
    this.abandonPaymentFactor = settings.abandonPaymentFactor;
    this.delayBetweenLoops = settings.delayBetweenLoops;
  }

  start(): void {
    setTimeout(() => {
      void this.generateData();
      this.timer = setInterval(() => void this.generateData(), FIXED_RATE_MS);
    }, INITIAL_DELAY_MS);
  }

  stop(): void {
    if (this.timer) {
      clearInterval(this.timer);
    }
  }

  updateInventoryCreationConfiguration(sizeOfBikeInventory: number, bikeType: string): void {
    this.inventorySize = sizeOfBikeInventory;
    this.inventoryBikeType = bikeType;
  }

  updateRentalGenerationConfiguration(
    rentalBikeType: string,
    loops: number,
    concurrency: number,
    abandonPaymentFactor: number,
    delay: number,
  ): void {
    this.rentalBikeType = rentalBikeType;
    this.loops = loops;
    this.concurrency = concurrency;
    this.abandonPaymentFactor = abandonPaymentFactor;
    this.delayBetweenLoops = delay;
  }

  private async generateData(): Promise<void> {
    try {
      await this.generateBikes();
    } catch (error) {
      logger.error('error generating inventory', error);
    }
    void this.generateRentals();
  }

  private async generateBikes(): Promise<void> {
    const currentBikeCount =
      (await this.queryDispatcher.query<number>(
        CountOfBikesByTypeQuery.TYPE,
        new CountOfBikesByTypeQuery(this.inventoryBikeType),
        JavaTypes.LONG,
      )) ?? 0;

    if (currentBikeCount < this.inventorySize) {
      for (let i = 0; i < 10; i++) {
        void this.commandDispatcher.send(
          new RegisterBikeCommand(randomUUID(), this.inventoryBikeType, this.dataGenerator.randomLocation()),
        );
      }
    }
  }

  private generateRentals(): Promise<void> {
    return this.dataGenerator.generateRentals(
      this.rentalBikeType,
      this.loops,
      this.concurrency,
      this.abandonPaymentFactor,
      this.delayBetweenLoops,
      (line) => logger.info(line.trim()),
    );
  }
}
