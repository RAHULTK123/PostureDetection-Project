# -*- mode: python ; coding: utf-8 -*-


a = Analysis(
    ['main.py'],
    pathex=[],
    binaries=[],
    datas=[('/home/srujan/Documents/Projects/posture_detection/myenv/lib/python3.12/site-packages/mediapipe/modules/pose_landmark/pose_landmark_cpu.binarypb', 'mediapipe/modules/pose_landmark'), ('/home/srujan/Documents/Projects/posture_detection/myenv/lib/python3.12/site-packages/mediapipe/modules/pose_landmark/pose_landmark_full.tflite', 'mediapipe/modules/pose_landmark'), ('/home/srujan/Documents/Projects/posture_detection/myenv/lib/python3.12/site-packages/mediapipe/modules/pose_detection/pose_detection.tflite', 'mediapipe/modules/pose_detection')],
    hiddenimports=['tkinter', 'Pillow._tkinter_finder', 'tkinter._fix', 'tkinter._fiximport','pygame',
    'pygame._view',],
    hookspath=['.'],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='main',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
