import { HealthCheckService, TerminusModule } from '@nestjs/terminus';
import { HealthCheckExecutor } from '@nestjs/terminus/dist/health-check/health-check-executor.service';
import { Test, TestingModule } from '@nestjs/testing';
import HealthController from './health.controller';
import { HttpException } from '@nestjs/common';

describe('HealthController', () => {
    let controller: HealthController;
    let healthService: HealthCheckService;

    beforeEach(async () => {
        const module: TestingModule = await Test.createTestingModule({
            controllers: [HealthController],
            imports: [TerminusModule]
        }).compile();

        controller = module.get<HealthController>(HealthController);
        healthService = module.get<HealthCheckService>(HealthCheckService);
    });

    it('should be defined', () => {
        expect(controller).toBeDefined();
    });

    it('should return successful health check', async () => {
        const mockResult = {
            status: 'ok',
            info: {
                referenceData: {
                    status: 'up',
                    details: {
                        service: 'reference-data',
                        timestamp: expect.any(String)
                    }
                }
            },
            error: {},
            details: {
                referenceData: {
                    status: 'up',
                    details: {
                        service: 'reference-data',
                        timestamp: expect.any(String)
                    }
                }
            }
        };

        const health = await controller.check();
        expect(health).toMatchObject(mockResult);
    });

    it('should handle errors appropriately', async () => {
        jest.spyOn(healthService, 'check').mockRejectedValue(new Error('Test error'));
        
        try {
            await controller.check();
            fail('Should have thrown an error');
        } catch (error) {
            expect(error).toBeInstanceOf(HttpException);
            expect(error.getStatus()).toBe(500);
            expect(error.getResponse()).toEqual({
                status: 'error',
                message: 'Test error'
            });
        }
    });
});