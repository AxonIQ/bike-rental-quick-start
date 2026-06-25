// A minimal leveled logger, the stand-in for SLF4J. The level is controlled by the LOG_LEVEL env var
// (error < warn < info < debug); the default is info.

type Level = 'error' | 'warn' | 'info' | 'debug';

const ORDER: Record<Level, number> = { error: 0, warn: 1, info: 2, debug: 3 };

function threshold(): number {
  const configured = (process.env.LOG_LEVEL ?? 'info').toLowerCase() as Level;
  return ORDER[configured] ?? ORDER.info;
}

export interface Logger {
  error(message: string, error?: unknown): void;
  warn(message: string): void;
  info(message: string): void;
  debug(message: string): void;
}

export function createLogger(name: string): Logger {
  const log = (level: Level, message: string, error?: unknown) => {
    if (ORDER[level] > threshold()) {
      return;
    }
    const line = `${new Date().toISOString()} ${level.toUpperCase().padEnd(5)} ${name} : ${message}`;
    if (level === 'error') {
      console.error(line, error ?? '');
    } else if (level === 'warn') {
      console.warn(line);
    } else {
      console.log(line);
    }
  };
  return {
    error: (message, error) => log('error', message, error),
    warn: (message) => log('warn', message),
    info: (message) => log('info', message),
    debug: (message) => log('debug', message),
  };
}
