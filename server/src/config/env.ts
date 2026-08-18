import { z } from 'zod';
const schema = z.object({ NODE_ENV: z.enum(['development', 'test', 'production']).default('development'), PORT: z.coerce.number().int().positive().default(3000), MONGODB_URI: z.string().min(1).optional(), JWT_SECRET: z.string().min(32).optional() });
export const env = schema.parse(process.env);

