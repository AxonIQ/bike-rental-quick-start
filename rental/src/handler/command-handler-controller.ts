import { Router } from 'express';
import type { Command, CommandResult } from '@bikerental/core-api';
import { BikeCommandHandler } from '../command/bike-command-handler.js';

/**
 * Receives commands that Axon Server routes to this application over the Integration HTTP API. It
 * hands the command to the {@link BikeCommandHandler} and returns a command result; a failure is
 * reported as a 500 so Axon Server relays it to the original sender.
 */
export function commandHandlerRouter(commandHandler: BikeCommandHandler): Router {
  const router = Router();
  router.post('/axon/command', async (req, res) => {
    const command = req.body as Command;
    try {
      const payload = await commandHandler.handle(command);
      const result: CommandResult = { id: command.id, payload: payload ?? null };
      res.json(result);
    } catch (error) {
      res.status(500).json({ message: String((error as Error)?.message) });
    }
  });
  return router;
}
