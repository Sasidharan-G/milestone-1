import https from 'node:https';
import { AppError } from '../../core/errors';
import { SmsSender } from '../contracts';

export interface Msg91Config {
  widgetId: string;
  tokenAuth: string;
  authKey?: string;
}

export class Msg91SmsSender implements SmsSender {
  constructor(private readonly config: Msg91Config) {}

  async sendOtp(phone: string, code: string): Promise<string> {
    const formattedPhone = phone.startsWith('+') ? phone : (phone.startsWith('91') && phone.length === 12 ? `+${phone}` : `+91${phone}`);
    return new Promise((resolve, reject) => {
      const payload = JSON.stringify({
        widgetId: this.config.widgetId,
        tokenAuth: this.config.tokenAuth,
        identifier: formattedPhone,
        otp: code
      });

      const headers: Record<string, string | number> = {
        'Content-Type': 'application/json',
        'tokenAuth': this.config.tokenAuth,
        'Content-Length': Buffer.byteLength(payload)
      };
      if (this.config.authKey) {
        headers['authkey'] = this.config.authKey;
      }

      const req = https.request(`https://control.msg91.com/api/v5/widget/sendOtp?widgetId=${this.config.widgetId}`, {
        method: 'POST',
        headers,
        timeout: 10000
      }, (res) => {
        let body = '';
        res.on('data', (chunk) => { body += chunk; });
        res.on('end', () => {
          try {
            const parsed = JSON.parse(body);
            if (parsed.type === 'success' && parsed.message) {
              resolve(String(parsed.message));
            } else {
              console.error(`[MSG91] sendOtp error response: ${body}`);
              const errMsg = parsed.message || 'OTP delivery via MSG91 failed';
              if (errMsg.toLowerCase().includes('credits')) {
                reject(new AppError(503, 'OTP_OUT_OF_CREDITS', 'SMS service is temporarily out of credits. Please recharge your MSG91 wallet.', true));
              } else {
                reject(new AppError(502, 'OTP_DELIVERY_FAILED', errMsg, true));
              }
            }
          } catch {
            console.error(`[MSG91] sendOtp non-JSON response: ${body}`);
            reject(new AppError(502, 'OTP_DELIVERY_FAILED', 'Invalid response from MSG91 SMS service', true));
          }
        });
      });

      req.on('timeout', () => {
        req.destroy();
        reject(new AppError(504, 'OTP_TIMEOUT', 'SMS service timed out. Please try again.', true));
      });

      req.on('error', (err) => {
        console.error(`[MSG91] sendOtp network error: ${err.message}`);
        reject(new AppError(502, 'OTP_DELIVERY_FAILED', `SMS service error: ${err.message}`, true));
      });

      req.write(payload);
      req.end();
    });
  }

  async verifyOtp(reqId: string, otp: string): Promise<boolean> {
    return new Promise((resolve) => {
      const payload = JSON.stringify({
        widgetId: this.config.widgetId,
        tokenAuth: this.config.tokenAuth,
        reqId,
        otp
      });

      const headers: Record<string, string | number> = {
        'Content-Type': 'application/json',
        'tokenAuth': this.config.tokenAuth,
        'Content-Length': Buffer.byteLength(payload)
      };
      if (this.config.authKey) {
        headers['authkey'] = this.config.authKey;
      }

      const req = https.request(`https://control.msg91.com/api/v5/widget/verifyOtp?widgetId=${this.config.widgetId}`, {
        method: 'POST',
        headers,
        timeout: 10000
      }, (res) => {
        let body = '';
        res.on('data', (chunk) => { body += chunk; });
        res.on('end', () => {
          try {
            const parsed = JSON.parse(body);
            resolve(parsed.type === 'success');
          } catch {
            resolve(false);
          }
        });
      });

      req.on('timeout', () => {
        req.destroy();
        resolve(false);
      });

      req.on('error', () => resolve(false));
      req.write(payload);
      req.end();
    });
  }
}
