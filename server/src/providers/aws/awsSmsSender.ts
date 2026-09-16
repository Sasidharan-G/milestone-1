import { PublishCommand, SNSClient } from '@aws-sdk/client-sns';
import { AppError } from '../../core/errors';
import { SmsSender } from '../contracts';

export class AwsSmsSender implements SmsSender {
  constructor(private readonly client: SNSClient) {}

  async sendOtp(phone: string, code: string): Promise<void> {
    try {
      await this.client.send(new PublishCommand({
        PhoneNumber: `+91${phone}`,
        Message: `Your KadaiKutty POS verification code is ${code}. Valid for 10 minutes. Do not share it.`,
        MessageAttributes: { 'AWS.SNS.SMS.SMSType': { DataType: 'String', StringValue: 'Transactional' } }
      }));
    } catch (error) {
      console.error('[AWS SNS] OTP delivery failed:', error instanceof Error ? error.message : error);
      throw new AppError(502, 'OTP_DELIVERY_FAILED', 'Unable to deliver OTP at this time. Please try again shortly.', true);
    }
  }
}
