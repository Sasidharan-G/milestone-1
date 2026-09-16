import { promises as fs } from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { AppError } from '../../core/errors';
import { emptyLocalState, LocalState } from './localState';

const delay = (milliseconds: number) => new Promise(resolve => setTimeout(resolve, milliseconds));

export class AtomicJsonStore {
  private readonly lockPath: string;

  constructor(private readonly filePath: string) {
    this.lockPath = `${filePath}.lock`;
  }

  async read<T>(reader: (state: Readonly<LocalState>) => T | Promise<T>): Promise<T> {
    return reader(await this.load());
  }

  async write<T>(writer: (state: LocalState) => T | Promise<T>): Promise<T> {
    const release = await this.acquireLock();
    try {
      const state = await this.load();
      const result = await writer(state);
      await this.persist(state);
      return result;
    } finally {
      await release();
    }
  }

  private async load(): Promise<LocalState> {
    try {
      const text = await fs.readFile(this.filePath, 'utf8');
      const parsed = JSON.parse(text) as LocalState;
      if (parsed.schemaVersion !== 1) throw new Error('Unsupported local state schema');
      return parsed;
    } catch (error: any) {
      if (error?.code === 'ENOENT') return emptyLocalState();
      throw new AppError(500, 'LOCAL_STORE_READ_FAILED', 'Local development store could not be read', false);
    }
  }

  private async persist(state: LocalState): Promise<void> {
    await fs.mkdir(path.dirname(this.filePath), { recursive: true });
    const temporary = `${this.filePath}.${process.pid}.${randomUUID()}.tmp`;
    const handle = await fs.open(temporary, 'wx', 0o600);
    try {
      await handle.writeFile(JSON.stringify(state, null, 2), 'utf8');
      await handle.sync();
    } finally {
      await handle.close();
    }
    await fs.rename(temporary, this.filePath);
  }

  private async acquireLock(): Promise<() => Promise<void>> {
    await fs.mkdir(path.dirname(this.filePath), { recursive: true });
    const deadline = Date.now() + 10_000;
    while (Date.now() < deadline) {
      try {
        const handle = await fs.open(this.lockPath, 'wx', 0o600);
        await handle.writeFile(JSON.stringify({ pid: process.pid, createdAtEpochMs: Date.now() }));
        return async () => {
          await handle.close();
          await fs.rm(this.lockPath, { force: true });
        };
      } catch (error: any) {
        if (error?.code !== 'EEXIST') throw error;
        const stat = await fs.stat(this.lockPath).catch(() => null);
        if (stat && Date.now() - stat.mtimeMs > 30_000) {
          await fs.rm(this.lockPath, { force: true });
          continue;
        }
        await delay(25 + Math.floor(Math.random() * 50));
      }
    }
    throw new AppError(503, 'LOCAL_STORE_BUSY', 'Local development store is busy', true);
  }
}

