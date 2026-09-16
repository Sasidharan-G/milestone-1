import { SmsSender } from '../contracts';

/** Development transport: the code is printed to the server console instead of being sent. */
export class LocalSmsSender implements SmsSender {
  async sendOtp(phone: string, code: string): Promise<void> {
    console.log(`[local-sms] OTP for +91${phone}: ${code}`);
  }
}
