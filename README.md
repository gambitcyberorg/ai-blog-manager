# AI Blog Management System

A comprehensive AI-powered blog management and threat intelligence extraction system built with Java Spring Boot and Python. This system automatically scans, processes, and extracts intelligence from cybersecurity blogs and threat reports.

## 🚀 Features

- **Automated Blog Scanning**: Scheduled scanning of parent blog URLs
- **Content Extraction**: Intelligent extraction of blog content using multiple providers
- **Threat Intelligence Processing**: AI-powered analysis and intelligence extraction
- **Elasticsearch Integration**: Robust data storage and search capabilities
- **Python API Integration**: Hybrid Java-Python architecture for specialized processing
- **RESTful API**: Complete REST API for blog management operations

## 📁 Project Structure

```
├── ai-agents/                 # Main Spring Boot application
│   ├── src/main/java/        # Java source code
│   ├── src/main/resources/   # Configuration files
│   ├── Dockerfile-dev        # Development Docker configuration
│   └── README.md            # Detailed application documentation
├── common-platform/          # Shared platform components
├── gambit/                   # Python-based utilities and integrations
├── Parser/                   # PDF parsing utilities
├── raw_pdfs/                # Sample PDF files for testing
├── threat-hunt/             # Threat hunting utilities
└── train/                   # Training data and models
```

## 🛠️ Prerequisites

- **Java 17+** and **Maven 3.6+**
- **Python 3.8+** with required packages
- **Docker** (for Elasticsearch)
- **Git**

## 🚀 Quick Start

### 1. Start Elasticsearch

```bash
# Start Elasticsearch in Docker
docker run -d --name es01 -p 9200:9200 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  elasticsearch:8.11.1

# Wait for Elasticsearch to be ready (Bash/Git Bash/WSL)
echo "Waiting for Elasticsearch to become available..."
until curl -s "http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=1s" | grep -q '"status":"yellow"\|"status":"green"'; do
    printf "."
    sleep 5
done
echo "Elasticsearch is ready!"
```

### 2. Start the Application

```bash
# Navigate to the ai-agents directory
cd ai-blog-management

# Clean and start the Spring Boot application
mvn clean spring-boot:run -Dspring-boot.run.profiles=local
```

The application will be available at `http://localhost:7979`

## 📡 API Usage

### Extract Content from a PDF

```bash
curl -X GET "http://localhost:7979/ai-agent/blog-manager/extract-blog-content?url=https://www.cisa.gov/sites/default/files/publications/Joint-CISA-FBI-NSA_CSA_AA21-291A_BlackMatter_Ransomware.pdf"
```

### Add a Parent Blog for Scanning

```bash
curl -X POST http://localhost:7979/ai-agent/blog-manager/add-parent-blog \
  -H "Content-Type: application/json" \
  -d '{
    "parentUrl": "https://thedfirreport.com/",
    "scanIntervalHours": 24
  }'
```

### Trigger a Manual Scan

```bash
curl -X POST http://localhost:7979/ai-agent/blog-manager/trigger-scan \
  -H "Content-Type: application/json" \
  -d '{
    "parentUrl": "https://thedfirreport.com/",
    "parentUid": "31abf227-0880-474d-894c-030f101856bd"
  }'
```

### Get Child Blogs by Parent

```bash
curl -X GET "http://localhost:7979/ai-agent/blog-manager/child-blogs/31abf227-0880-474d-894c-030f101856bd"
```

## 🔍 Monitoring and Debugging

### Elasticsearch Operations

#### Delete Indices (Clean Slate)
```bash
curl -X DELETE "http://localhost:9200/article_scrape_status"
curl -X DELETE "http://localhost:9200/parent_blog_schedules"
curl -X DELETE "http://localhost:9200/threat-intel"
```

#### View Sample Documents
```bash
# View one document from each index
curl -X GET "http://localhost:9200/article_scrape_status/_search?pretty&size=1"
curl -X GET "http://localhost:9200/parent_blog_schedules/_search?pretty&size=1"
curl -X GET "http://localhost:9200/threat-intel/_search?pretty&size=1"
```

#### Count Articles by Status
```bash
# Count FAILED articles
curl -X GET "http://localhost:9200/article_scrape_status/_count?pretty" \
  -H "Content-Type: application/json" \
  -d '{"query":{"term":{"scrapeStatus":"FAILED"}}}'

# Count SUCCESS articles
curl -X GET "http://localhost:9200/article_scrape_status/_count?pretty" \
  -H "Content-Type: application/json" \
  -d '{"query":{"term":{"scrapeStatus":"SUCCESS"}}}'
```

## 🏗️ Architecture

### Core Components

1. **BlogManagerService**: Main orchestration service
2. **UrlCollectionService**: Collects URLs and identifies blog links using LLM
3. **ContentExtractionService**: Extracts content from web pages
4. **PythonApiService**: Interfaces with Python API for content preprocessing
5. **BlogManagerScheduler**: Automated scanning and retry logic

### Data Flow

1. **URL Discovery**: Crawls parent URLs to find candidate links
2. **Blog Identification**: Uses LLM to identify actual blog/article URLs
3. **Content Extraction**: Extracts content using Jsoup or Firecrawl
4. **Python API Integration**: Calls Python service for content preprocessing
5. **Intelligence Extraction**: Processes content through CTI pipeline
6. **Data Storage**: Stores results in Elasticsearch
