import { Controller, Get, HttpException, HttpStatus } from '@nestjs/common';
import { HealthCheckService, HealthCheck, HealthCheckResult } from '@nestjs/terminus';

@Controller('health')
class HealthController {
    constructor(private health: HealthCheckService) {}

    @Get()
    @HealthCheck()
    async check(): Promise<HealthCheckResult> {
        try {
            const result = await this.health.check([
                // Add specific health checks here
                async () => ({
                    referenceData: {
                        status: 'up',
                        details: {
                            service: 'reference-data',
                            timestamp: new Date().toISOString()
                        }
                    }
                })
            ]);

            return result;
        } catch (error) {
            if (error instanceof HttpException) {
                throw error;
            }
            throw new HttpException(
                {
                    status: 'error',
                    message: error.message,
                },
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}
export default HealthController;