using Microsoft.AspNetCore.Mvc;
using System;
using System.Threading.Tasks;

namespace PeopleService.WebApi.Controllers
{
    [ApiController]
    [Route("[controller]")]
    public class HealthController : ControllerBase
    {
        private readonly ILogger<HealthController> _logger;

        public HealthController(ILogger<HealthController> logger)
        {
            _logger = logger;
        }

        [HttpGet]
        public async Task<IActionResult> Get()
        {
            try
            {
                var healthStatus = new
                {
                    Service = "people-service",
                    Status = "UP",
                    Timestamp = DateTimeOffset.UtcNow,
                    Version = GetType().Assembly.GetName().Version?.ToString()
                };

                return Ok(healthStatus);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Health check failed");
                return StatusCode(500, new
                {
                    Status = "DOWN",
                    Error = ex.Message,
                    Timestamp = DateTimeOffset.UtcNow
                });
            }
        }
    }
}
