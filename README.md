# Web Grading System - Source Code Mono-Repo

Mono-repository chứa toàn bộ source code của các microservices trong hệ thống Web Grading System.

## 🏗️ Cấu trúc

```
src-services/
├── .github/
│   └── workflows/
│       └── build-services.yml    # CI/CD workflow tự động detect và build services
├── submission-service/           # Service xử lý submissions
├── executor-service/             # Service thực thi code
├── api-gateway/                  # API Gateway
├── result-service/               # Service quản lý kết quả
├── assignment-service/           # Service quản lý bài tập
```

## 🚀 CI/CD Pipeline

### Cách hoạt động

1. **Detect Changes**: Workflow tự động phát hiện service nào thay đổi
2. **Smart Build**: Chỉ build các services bị ảnh hưởng
4. **Docker Push**: Build và push Docker image lên DockerHub
5. **Config Update**: Tự động cập nhật version trong config repo

### Branch Strategy

- **main**: Production release với semantic versioning (`v1.0.0`, `v1.0.1`, ...)
- **develop**: Development builds
- **feat/\***: Feature branches với tag dạng `feat-feature-name-<commit-hash>`

### Versioning

Mỗi service có version riêng theo format: `<service-name>-v<version>`

Ví dụ:
- `submission-service-v1.0.0`
- `api-gateway-v1.2.3`

### Docker Images

Images được push lên DockerHub với naming convention:
```
<dockerhub-username>/web-grading-system-<service-name>:<version>
<dockerhub-username>/web-grading-system-<service-name>:latest
```

## 🔧 Setup

### Prerequisites

- JDK 21
- Maven 3.8+
- Docker (nếu build local)

### Build Local

```bash
# Build một service cụ thể
cd submission-service
mvn clean package

# Build Docker image
docker build -t web-grading-system-submission-service:dev .
```

### GitHub Secrets Required

Cấu hình các secrets sau trong GitHub repository:

- `DOCKERHUB_USERNAME`: Username DockerHub
- `DOCKERHUB_TOKEN`: DockerHub access token
- `CONFIG_REPO_TOKEN`: GitHub Personal Access Token có quyền write vào config repo

## 📝 Development Workflow

### 1. Tạo feature branch

```bash
git checkout -b feat/my-feature
```

### 2. Thực hiện thay đổi

Chỉ cần sửa code trong service folder tương ứng.

### 3. Push và tạo PR

```bash
git add .
git commit -m "feat(submission-service): add new feature"
git push origin feat/my-feature
```

Workflow sẽ tự động:
- Detect service thay đổi
- Build và test
- Push Docker image với tag feature

### 4. Merge vào main

Sau khi merge PR vào `main`:
- Version tự động tăng
- Tag được tạo: `<service>-v<version>`
- Docker image với version mới được push
- Config repo được update tự động

## 🎯 Services

### submission-service
Port: 8082  
Xử lý việc submit bài tập từ students.

### executor-service
Thực thi code trong môi trường sandbox an toàn.

### api-gateway
Port: 8080  
Gateway chính cho toàn bộ hệ thống.

### result-service
Quản lý và tính toán kết quả grading.

### assignment-service
Quản lý assignments và test cases.

## 🔗 Related Repositories

- **Config Repo**: [web-grading-system-config](https://github.com/PTIT-DTL-Project/web-grading-system-config) - Helm charts và values
- **Deploy Repo**: [web-grading-system-deploy](https://github.com/PTIT-DTL-Project/web-grading-system-deploy) - ArgoCD orchestration

## 📚 Documentation

Chi tiết về architecture và deployment xem tại [deploy repo](https://github.com/PTIT-DTL-Project/web-grading-system-deploy).
