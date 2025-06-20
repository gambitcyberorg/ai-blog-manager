# Blog Manager - Java Implementation

This is a Java Spring Boot implementation of the blog manager pipeline that replicates the Python functionality from `blog_manager.py`. It uses Elasticsearch instead of PostgreSQL for data storage and integrates with the Python API for content preprocessing.

## Architecture Overview

The blog manager consists of several key components:

### 1. Domain Models
- **ParentBlogSchedule**: Stores parent blog URLs and their scanning schedules
- **ArticleScrapeStatus**: Tracks the status of individual article processing

### 2. Services
- **BlogManagerService**: Main orchestration service
- **UrlCollectionService**: Collects URLs and identifies blog links using LLM (Java equivalent of `blog_link_gen.py`)
- **ContentExtractionService**: Extracts content from web pages using Jsoup/Firecrawl
- **PythonApiService**: Interfaces with the Python API for content preprocessing

### 3. Controllers
- **BlogManagerController**: REST API endpoints for blog management

### 4. Scheduler
- **BlogManagerScheduler**: Automated scanning and retry logic

## Key Features

### URL Collection & Blog Identification
- Uses **Jsoup** (Java equivalent of BeautifulSoup) for web scraping
- Implements the same URL collection logic as the Python version
- Uses Spring AI ChatClient for LLM-based blog link identification
- Supports rate limiting and error handling

### Content Processing Pipeline
1. **URL Discovery**: Crawls parent URLs to find candidate links
2. **Blog Identification**: Uses LLM to identify actual blog/article URLs
3. **Content Extraction**: Extracts content using Jsoup or Firecrawl
4. **Python API Integration**: Calls Python service for content preprocessing
5. **Intelligence Extraction**: Processes content through CTI pipeline
6. **Data Storage**: Stores results in Elasticsearch

### Elasticsearch Integration
- Replaces PostgreSQL with Elasticsearch for better search capabilities
- Uses Spring Data Elasticsearch repositories
- Maintains the same data structure as the Python version

## API Endpoints

### Add Parent Blog
```bash
POST /ai-agent/blog-manager/add-parent-blog
Content-Type: application/json

{
  "parentUrl": "https://thedfirreport.com/",
  "scanIntervalHours": 24
}
```

### Extract Blog Content
```bash
GET /ai-agent/blog-manager/extract-blog-content?url=https://example.com/article&provider=azure
```

### Get Parent Blog UIDs
```bash
GET /ai-agent/blog-manager/parent-blogs-uids
```

### Get Child Blogs
```bash
GET /ai-agent/blog-manager/child-blogs/{parentUid}
```

### Manual Triggers
```bash
# Trigger manual scan
POST /ai-agent/blog-manager/trigger-scan
{
  "parentUrl": "https://thedfirreport.com/",
  "parentUid": "uuid-here"
}

# Run scheduled scans
POST /ai-agent/blog-manager/run-scheduled-scans

# Retry failed articles
POST /ai-agent/blog-manager/retry-failed-articles
```

### Health Check
```bash
GET /ai-agent/blog-manager/python-api-health
```

## Configuration

The blog manager can be configured via `application-local.yml`:

```yaml
blog-manager:
  scheduler:
    enabled: true
  default-scan-interval-hours: 168  # Weekly
  default-retry-interval-minutes: 60
  max-failure-count: 5
  python-provider: azure

python:
  api:
    base-url: http://localhost:8000
    timeout: 60

firecrawl:
  url: https://api.firecrawl.dev/v0
  api:
    key: Bearer your-api-key
```

## Java Alternatives to Python Libraries

| Python Library | Java Alternative | Usage |
|----------------|------------------|-------|
| BeautifulSoup | Jsoup | HTML parsing and web scraping |
| Selenium | HtmlUnit (if needed) | JavaScript-heavy sites |
| requests | WebClient (Spring WebFlux) | HTTP requests |
| asyncio | Reactor (Project Reactor) | Asynchronous programming |
| psycopg2 | Spring Data Elasticsearch | Data persistence |
| FastAPI | Spring Boot Web | REST API framework |

## Workflow

1. **Parent URL Addition**: User adds a parent blog URL via API
2. **Scheduled Scanning**: Scheduler runs every 5 minutes to check for due scans
3. **URL Collection**: Uses Jsoup to crawl and collect candidate URLs
4. **Blog Identification**: LLM identifies actual blog/article URLs
5. **Content Processing**: For each identified URL:
   - Calls Python API for content extraction and preprocessing
   - Stores intelligence data via external APIs
   - Updates article status in Elasticsearch
6. **Retry Logic**: Failed articles are retried with exponential backoff
7. **Monitoring**: Health checks and logging for observability

## Integration with Python Service

The Java implementation integrates with the existing Python service for:
- Content extraction and preprocessing (calls `/pdf-parser/extract`)
- Intelligence extraction (calls external CTI APIs)
- Data storage (calls database APIs)

This hybrid approach leverages the best of both worlds:
- Java for robust enterprise features, scheduling, and API management
- Python for specialized content processing and ML/AI operations

## Running the Application

1. Ensure Elasticsearch is running on `localhost:9200`
2. Ensure Python service is running on `localhost:8000`
3. Start the Spring Boot application:
   ```bash
   mvn spring-boot:run
   ```
4. Access the API at `http://localhost:7979/ai-agent/blog-manager/`

## Monitoring and Debugging

- Logs are available at DEBUG level for detailed tracing
- Health check endpoint monitors Python API connectivity
- Elasticsearch indices can be queried directly for debugging
- Manual trigger endpoints allow testing individual components

This implementation provides a production-ready, scalable blog management system that maintains compatibility with the existing Python pipeline while adding enterprise-grade features and monitoring capabilities. 