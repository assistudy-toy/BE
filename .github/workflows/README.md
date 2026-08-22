# GitHub Actions — 필요한 Secrets

Settings → Secrets and variables → Actions 에서 등록

## AWS 인증
| Secret | 설명 |
|--------|------|
| `AWS_ACCOUNT_ID` | AWS 계정 ID (12자리 숫자) |
| `AWS_ACCESS_KEY_ID` | CI 전용 IAM User의 Access Key |
| `AWS_SECRET_ACCESS_KEY` | CI 전용 IAM User의 Secret Key |

## CI 전용 IAM User 최소 권한
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload",
        "ecr:PutImage"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ecs:UpdateService",
        "ecs:DescribeServices"
      ],
      "Resource": "arn:aws:ecs:ap-northeast-2:{account}:service/assistudy-prod/*"
    }
  ]
}
```
