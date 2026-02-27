import http.client
import json
import base64
from datetime import datetime


TOKEN_FILE = "D:/gemini_token.txt"


def load_token():
    """从文件读取 API token"""
    try:
        with open(TOKEN_FILE, "r") as f:
            return f.read().strip()
    except FileNotFoundError:
        print(f"错误: 请在 {TOKEN_FILE} 中配置 API token")
        return None


def generate_image(prompt: str, output_path: str = None, aspect_ratio: str = "1:1", image_size: str = "0.5K"):
    """
    使用 Gemini API 生成图片

    Args:
        prompt: 图片描述提示词
        output_path: 输出文件路径，默认为 generated_{timestamp}.png
        aspect_ratio: 宽高比，如 "1:1", "9:16", "16:9"
        image_size: 图片尺寸，如 "0.5K", "1K"

    Returns:
        保存的文件路径，失败返回 None
    """
    token = load_token()
    if not token:
        return None

    conn = http.client.HTTPSConnection("api.vectorengine.ai")
    payload = json.dumps({
        "contents": [
            {
                "role": "user",
                "parts": [
                    {
                        "text": prompt
                    }
                ]
            }
        ],
        "generationConfig": {
            "responseModalities": [
                "TEXT",
                "IMAGE"
            ],
            "imageConfig": {
                "aspectRatio": aspect_ratio,
                "imageSize": image_size
            }
        }
    })
    headers = {
        'Authorization': f'Bearer {token}',
        'Content-Type': 'application/json'
    }

    conn.request("POST", "/v1beta/models/gemini-3.1-flash-image-preview:generateContent?key=", payload, headers)
    res = conn.getresponse()
    data = res.read()

    response = json.loads(data.decode("utf-8"))

    # 解析响应，提取图片数据
    if "candidates" in response:
        for candidate in response["candidates"]:
            if "content" in candidate and "parts" in candidate["content"]:
                for part in candidate["content"]["parts"]:
                    if "inlineData" in part:
                        image_data = part["inlineData"]["data"]
                        mime_type = part["inlineData"].get("mimeType", "image/png")

                        # 生成输出路径
                        if output_path is None:
                            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
                            output_path = f"generated_{timestamp}.png"

                        # 解码 base64 并保存
                        image_bytes = base64.b64decode(image_data)
                        with open(output_path, "wb") as f:
                            f.write(image_bytes)

                        print(f"图片已保存: {output_path}")
                        return output_path

    print(f"生成失败: {response}")
    return None


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="使用 Gemini API 生成图片")
    parser.add_argument("prompt", help="图片描述提示词")
    parser.add_argument("-o", "--output", default=None, help="输出文件路径")
    parser.add_argument("--ratio", default="1:1", help="宽高比 (1:1, 9:16, 16:9)")
    parser.add_argument("--size", default="0.5K", help="图片尺寸 (0.5K, 1K)")
    args = parser.parse_args()

    generate_image(args.prompt, args.output, args.ratio, args.size)