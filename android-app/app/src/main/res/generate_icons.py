import os
from PIL import Image

def generate_icons(source_image_path, res_dir):
    sizes = {
        'mdpi': 48,
        'hdpi': 72,
        'xhdpi': 96,
        'xxhdpi': 144,
        'xxxhdpi': 192
    }
    
    try:
        img = Image.open(source_image_path)
    except Exception as e:
        print(f"Error opening image: {e}")
        return
        
    for density, size in sizes.items():
        mipmap_dir = os.path.join(res_dir, f'mipmap-{density}')
        os.makedirs(mipmap_dir, exist_ok=True)
        
        # Resize image
        resized = img.resize((size, size), Image.Resampling.LANCZOS)
        
        # Save as ic_launcher.png
        resized.save(os.path.join(mipmap_dir, 'ic_launcher.png'), 'PNG')
        
        # Mask for round icon
        mask = Image.new('L', (size, size), 0)
        from PIL import ImageDraw
        draw = ImageDraw.Draw(mask)
        draw.ellipse((0, 0, size, size), fill=255)
        
        round_img = resized.copy()
        round_img.putalpha(mask)
        round_img.save(os.path.join(mipmap_dir, 'ic_launcher_round.png'), 'PNG')
        
        print(f"Generated new mass icons for {density} ({size}x{size})")

if __name__ == '__main__':
    source = r"C:\Users\SasiDharan G\.gemini\antigravity-ide\brain\819b75bc-f4b8-43b9-8c3a-e6e025b45504\kadakutty_mass_icon_1787245420906.jpg"
    res = r"C:\Users\SasiDharan G\OneDrive\Desktop\milestone-1\android-app\app\src\main\res"
    generate_icons(source, res)
