import { randomUUID } from 'node:crypto';
import {
  BikeStatus,
  BikeStatusNamedQueries,
  CommandDispatcher,
  ConfirmPaymentCommand,
  JavaTypes,
  PaymentStatusNamedQueries,
  QueryDispatcher,
  RegisterBikeCommand,
  RentalStatus,
  RequestBikeCommand,
  ReturnBikeCommand,
} from '@bikerental/core-api';

const RENTERS = ['Allard', 'Steven', 'Josh', 'David', 'Marc', 'Sara', 'Milan', 'Jeroen', 'Marina', 'Jeannot'];
const LOCATIONS = ['Amsterdam', 'Paris', 'Vilnius', 'Barcelona', 'London', 'New York', 'Toronto', 'Berlin', 'Milan', 'Rome', 'Belgrade'];

const delay = (ms: number): Promise<void> => new Promise((resolve) => setTimeout(resolve, ms));

function randomInt(boundExclusive: number): number {
  return Math.floor(Math.random() * boundExclusive);
}

function randomIntBetween(originInclusive: number, boundExclusive: number): number {
  return originInclusive + Math.floor(Math.random() * (boundExclusive - originInclusive));
}

export class BikeRentalDataGenerator {
  constructor(
    private readonly commandDispatcher: CommandDispatcher,
    private readonly queryDispatcher: QueryDispatcher,
  ) {}

  randomRenter(): string {
    return RENTERS[randomInt(RENTERS.length)];
  }

  randomLocation(): string {
    return LOCATIONS[randomInt(LOCATIONS.length)];
  }

  async generateBikes(bikeCount: number, bikeType: string): Promise<void> {
    const sends: Promise<void>[] = [];
    for (let i = 0; i < bikeCount; i++) {
      sends.push(
        this.commandDispatcher.send(new RegisterBikeCommand(randomUUID(), bikeType, this.randomLocation())),
      );
    }
    await Promise.all(sends);
  }

  /**
   * Runs `loops` rental cycles with at most `concurrency` in flight, reporting one result line per
   * cycle through `onLine` (mirroring the Flux the Java controller streamed back).
   */
  async generateRentals(
    bikeType: string,
    loops: number,
    concurrency: number,
    abandonPaymentFactor: number,
    delayMs: number,
    onLine: (line: string) => void,
  ): Promise<void> {
    let next = 0;
    const worker = async (): Promise<void> => {
      for (;;) {
        const index = next++;
        if (index >= loops) {
          return;
        }
        try {
          await this.executeRentalCycle(bikeType, this.randomRenter(), abandonPaymentFactor, delayMs);
          onLine('OK - Rented, Payed and Returned\n');
        } catch (error) {
          onLine(`Not ok: ${(error as Error).message}\n`);
        }
      }
    };
    const workers = Array.from({ length: Math.max(1, Math.min(concurrency, loops)) }, worker);
    await Promise.all(workers);
  }

  private async executeRentalCycle(
    bikeType: string,
    renter: string,
    abandonPaymentFactor: number,
    delayMs: number,
  ): Promise<string> {
    const bikeId = await this.selectRandomAvailableBike(bikeType);
    const paymentRef = (await this.commandDispatcher.send<string>(
      new RequestBikeCommand(bikeId, renter),
      JavaTypes.STRING,
    ))!;
    await delay(this.randomDelay(delayMs));
    await this.executePayment(bikeId, paymentRef, abandonPaymentFactor);
    await this.whenBikeUnlocked(bikeId);
    await delay(this.randomDelay(delayMs));
    await this.commandDispatcher.send(new ReturnBikeCommand(bikeId, this.randomLocation()));
    return bikeId;
  }

  private async selectRandomAvailableBike(bikeType: string): Promise<string> {
    const available = await this.queryDispatcher.queryMany<BikeStatus>(
      BikeStatusNamedQueries.FIND_AVAILABLE,
      bikeType,
      BikeStatus.TYPE,
    );
    return this.pickRandom(available).getBikeId();
  }

  private async executePayment(
    bikeId: string,
    paymentRef: string,
    abandonPaymentFactor: number,
  ): Promise<string> {
    if (abandonPaymentFactor > 0 && randomInt(abandonPaymentFactor) === 0) {
      throw new Error('Customer refused to pay');
    }
    const paymentId = await this.queryDispatcher.pollFor<string>(
      PaymentStatusNamedQueries.GET_PAYMENT_ID,
      paymentRef,
      JavaTypes.STRING,
      (result) => result !== null,
    );
    await this.commandDispatcher.send(new ConfirmPaymentCommand(paymentId));
    return bikeId;
  }

  private async whenBikeUnlocked(bikeId: string): Promise<string> {
    await this.queryDispatcher.pollFor<BikeStatus>(
      BikeStatusNamedQueries.FIND_ONE,
      bikeId,
      BikeStatus.TYPE,
      (status) => status?.getStatus() === RentalStatus.RENTED,
    );
    return bikeId;
  }

  private randomDelay(delayMs: number): number {
    if (delayMs <= 0) {
      return 0;
    }
    return randomIntBetween(delayMs - (delayMs >> 2), delayMs + delayMs + (delayMs >> 2));
  }

  private pickRandom<T>(source: T[]): T {
    if (source.length === 0) {
      throw new Error('No available bike to pick');
    }
    return source[randomInt(source.length)];
  }
}
