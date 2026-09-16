import crypto, { randomUUID } from 'node:crypto';
import { promises as fs } from 'node:fs';
import path from 'node:path';
import { AppError } from '../../core/errors';
import { BackupRecord, ObjectStorage } from '../contracts';
import { AtomicJsonStore } from './atomicJsonStore';

export class LocalObjectStorage implements ObjectStorage {
  constructor(private readonly store: AtomicJsonStore, private readonly rootDirectory: string) {}

  async createUploadIntent(input: Omit<BackupRecord, 'backupId' | 'objectKey' | 'status' | 'createdAtEpochMs'>): Promise<BackupRecord & { uploadUrl: string }> {
    const backupId = randomUUID();
    const backup: BackupRecord = {
      ...input, backupId, objectKey: `${input.companyId}/${backupId}.backup`, status: 'PENDING', createdAtEpochMs: Date.now()
    };
    await this.store.write(state => { state.backups[backupId] = backup; });
    return { ...backup, uploadUrl: `/api/v1/backups/${backupId}/content` };
  }

  async writeLocalContent(companyId: string, backupId: string, content: Buffer): Promise<void> {
    const backup = await this.requireBackup(companyId, backupId);
    if (content.length !== backup.sizeBytes) throw new AppError(422, 'BACKUP_SIZE_MISMATCH', 'Backup size does not match upload intent');
    const checksum = crypto.createHash('sha256').update(content).digest('hex');
    if (checksum !== backup.checksumSha256.toLowerCase()) throw new AppError(422, 'BACKUP_CHECKSUM_MISMATCH', 'Backup checksum verification failed');
    const filePath = this.filePath(backup);
    await fs.mkdir(path.dirname(filePath), { recursive: true });
    const temporary = `${filePath}.${process.pid}.tmp`;
    await fs.writeFile(temporary, content, { mode: 0o600 });
    await fs.rename(temporary, filePath);
  }

  async complete(companyId: string, backupId: string): Promise<BackupRecord> {
    const backup = await this.requireBackup(companyId, backupId);
    const stat = await fs.stat(this.filePath(backup)).catch(() => null);
    if (!stat || stat.size !== backup.sizeBytes) throw new AppError(409, 'BACKUP_UPLOAD_INCOMPLETE', 'Backup upload is incomplete');
    return this.store.write(state => {
      const current = state.backups[backupId];
      current.status = 'READY';
      return current;
    });
  }

  list(companyId: string): Promise<BackupRecord[]> {
    return this.store.read(state => Object.values(state.backups).filter(backup => backup.companyId === companyId && backup.status === 'READY').sort((left, right) => right.createdAtEpochMs - left.createdAtEpochMs));
  }

  async createDownloadIntent(companyId: string, backupId: string): Promise<{ backup: BackupRecord; downloadUrl: string }> {
    const backup = await this.requireBackup(companyId, backupId);
    if (backup.status !== 'READY') throw new AppError(409, 'BACKUP_NOT_READY', 'Backup is not ready for download');
    return { backup, downloadUrl: `/api/v1/backups/${backupId}/content` };
  }

  async readLocalContent(companyId: string, backupId: string): Promise<Buffer> {
    const backup = await this.requireBackup(companyId, backupId);
    if (backup.status !== 'READY') throw new AppError(409, 'BACKUP_NOT_READY', 'Backup is not ready for download');
    return fs.readFile(this.filePath(backup));
  }

  async delete(companyId: string, backupId: string): Promise<void> {
    const backup = await this.requireBackup(companyId, backupId);
    await fs.rm(this.filePath(backup), { force: true });
    await this.store.write(state => { delete state.backups[backupId]; });
  }

  private requireBackup(companyId: string, backupId: string): Promise<BackupRecord> {
    return this.store.read(state => {
      const backup = state.backups[backupId];
      if (!backup || backup.companyId !== companyId) throw new AppError(404, 'BACKUP_NOT_FOUND', 'Backup was not found');
      return backup;
    });
  }

  private filePath(backup: BackupRecord): string {
    return path.join(this.rootDirectory, backup.companyId, `${backup.backupId}.backup`);
  }
}

