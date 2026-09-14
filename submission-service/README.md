# Submission Service

## Version: Auto-managed by CI/CD

Submission Service handles all code submission operations in the Web Grading System.

### Features
- Accept code submissions from students
- Validate submission format
- Store submission metadata
- Queue submissions for execution

### Recent Updates (2026-08-06)
- ✨ Added `/api/v1/submissions/health` endpoint for health checks
- ✨ Added `/api/v1/submissions/version` endpoint for version information
- 🔧 Version now managed automatically by CI/CD pipeline
- 📝 Updated application metadata
- 🔧 Improved monitoring and observability support

### Changelog

#### Latest (Auto-versioned by CI/CD)
- Added health check endpoint with detailed service information
- Added version endpoint for CI/CD tracking
- Version auto-incremented based on git tags
- Enhanced logging for debugging
- Updated documentation

#### v1.0.0 (Initial)
- Basic submission handling
- RustFS integration for file storage
- PostgreSQL persistence
- RESTful API endpoints

### API Endpoints

#### Health & Version
- `GET /api/v1/submissions/health` - Service health check with version info
- `GET /api/v1/submissions/version` - Detailed version information

#### Submissions
- `POST /api/v1/submissions/presigned-url` - Request upload URL (query: `assignmentId`, `zipFileName`, optional `planId`)
- `GET /api/v1/submissions` - List user submissions (paginated, requires `X-User-Id`)
- `GET /api/v1/submissions/{id}` - Get submission details
- `GET /api/v1/submissions/assignment/{assignmentId}` - List by assignment
- `PUT /api/v1/submissions/{id}/status` - Update submission status (executor-internal)
- `POST /api/v1/submissions/webhook/upload-complete` - RustFS webhook, sole grading trigger

### Configuration

Required environment variables:
- `DB_HOST` - PostgreSQL host (default: localhost)
- `DB_PORT` - PostgreSQL port (default: 5432)
- `DB_NAME` - Database name (default: submission_db)
- `DB_USERNAME` - Database username
- `DB_PASSWORD` - Database password
- `RUSTFS_ENDPOINT` - RustFS/MinIO endpoint
- `RUSTFS_ACCESS_KEY` - RustFS access key
- `RUSTFS_SECRET_KEY` - RustFS secret key
- `RUSTFS_BUCKET_NAME` - Bucket name for submissions

### Test: GitHub Actions Workflow
This service demonstrates the complete CI/CD pipeline:
- ✅ Auto-detection of changed services
- ✅ Maven build process
- ✅ Docker image creation with version tags
- ✅ Automatic config repository update
- ✅ GitHub Release creation
- ✅ Version auto-increment (v1.0.0 → v1.0.1 → v1.0.2)

**Last updated**: 2026-08-06T13:30:00+07:00