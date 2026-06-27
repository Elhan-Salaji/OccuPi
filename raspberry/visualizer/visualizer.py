import math
import os
import sys
import time
from collections import deque

import matplotlib
# Native backend on macOS, default backend elsewhere (e.g. TkAgg on the Pi)
if sys.platform == 'darwin':
    matplotlib.use('macosx')
import matplotlib.pyplot as plt
import matplotlib.patches as patches
import numpy as np

from config import (
    BOUNDARY_X_MIN, BOUNDARY_X_MAX,
    BOUNDARY_Y_MIN, BOUNDARY_Y_MAX,
)

fig = None
ax = None
_scatter = None
_count_text = None
_target_circles = []
_target_labels = []
_trail_lines = []
_vel_lines = []
_trails = {}  # tid -> deque of (x, y)

# Blitting: redraw only the dynamic artists on top of a cached background
# instead of re-rendering the whole figure every update
_use_blit = False
_background = None
_dynamic_artists = []

_cbar = None
_color_mode = 'doppler'  # 'doppler' or 'height', toggled with the 'c' key

# Periodic PNG snapshot of the live view (e.g. for remote debugging).
# Set VISUALIZER_SNAPSHOT="" to disable.
_SNAPSHOT_PATH = os.getenv('VISUALIZER_SNAPSHOT', '/tmp/occupi_live.png')
_SNAPSHOT_INTERVAL = 2.0  # seconds
_last_snapshot = 0.0

x_range = BOUNDARY_X_MAX - BOUNDARY_X_MIN
y_range = BOUNDARY_Y_MAX - BOUNDARY_Y_MIN
_MAX_TARGETS = 10
_TRAIL_LEN = 60        # frames of position history (~3s at 55ms/frame)
_VEL_SCALE = 0.7       # arrow length = velocity * 0.7s lookahead
_DOPPLER_RANGE = 1.0   # m/s mapped to colormap extremes
_FOV_DEG = 70          # fovCfg azimuth FOV (+/- deg)

_colormap = plt.get_cmap('tab10')


def _target_color(tid: int):
    return _colormap(tid % 10)


def is_open() -> bool:
    return fig is not None and plt.fignum_exists(fig.number)


def update(point_cloud: list, targets: list):
    if not is_open():
        raise SystemExit("Visualizer window closed.")

    # Point cloud: position, color = doppler or height (toggle: 'c'), size = SNR
    if point_cloud:
        xy = np.array([[p["x"], p["y"]] for p in point_cloud], dtype=np.float32)
        if _color_mode == 'height':
            values = np.array([p.get("z", 0.0) for p in point_cloud], dtype=np.float32)
        else:
            values = np.array([p.get("doppler", 0.0) for p in point_cloud], dtype=np.float32)
        snr = np.array([p.get("snr", 10.0) for p in point_cloud], dtype=np.float32)
        sizes = np.clip(4 + snr * 0.4, 4, 40)
    else:
        xy = np.empty((0, 2), dtype=np.float32)
        values = np.empty(0, dtype=np.float32)
        sizes = np.empty(0, dtype=np.float32)
    _scatter.set_offsets(xy)
    _scatter.set_array(values)
    _scatter.set_sizes(sizes)

    # Tracked targets: circle, label, velocity vector and trail per track
    current_tids = set()
    for i, t in enumerate(targets[:_MAX_TARGETS]):
        tid, x, y = t["tid"], t["posX"], t["posY"]
        vx, vy = t.get("velX", 0.0), t.get("velY", 0.0)
        color = _target_color(tid)
        current_tids.add(tid)

        _target_circles[i].center = (x, y)
        _target_circles[i].set_edgecolor(color)
        _target_circles[i].set_visible(True)

        speed = (vx * vx + vy * vy) ** 0.5
        _target_labels[i].set_position((x, y + 0.45))
        _target_labels[i].set_text(f'ID {tid} · {speed:.1f} m/s')
        _target_labels[i].set_color(color)
        _target_labels[i].set_visible(True)

        _vel_lines[i].set_data([x, x + vx * _VEL_SCALE], [y, y + vy * _VEL_SCALE])
        _vel_lines[i].set_color(color)
        _vel_lines[i].set_visible(speed > 0.05)

        trail = _trails.setdefault(tid, deque(maxlen=_TRAIL_LEN))
        trail.append((x, y))
        pts = np.array(trail)
        _trail_lines[i].set_data(pts[:, 0], pts[:, 1])
        _trail_lines[i].set_color(color)
        _trail_lines[i].set_visible(len(trail) > 1)

    for i in range(len(targets), _MAX_TARGETS):
        _target_circles[i].set_visible(False)
        _target_labels[i].set_visible(False)
        _vel_lines[i].set_visible(False)
        _trail_lines[i].set_visible(False)

    # Drop history of tracks that disappeared
    for tid in list(_trails):
        if tid not in current_tids:
            del _trails[tid]

    _count_text.set_text(f'People: {len(targets)}   Points: {len(point_cloud)}')

    canvas = fig.canvas
    if _use_blit and _background is not None:
        canvas.restore_region(_background)
        for artist in _dynamic_artists:
            ax.draw_artist(artist)
        canvas.blit(fig.bbox)
    else:
        canvas.draw_idle()
    canvas.flush_events()
    _save_snapshot()


def _save_snapshot():
    """Periodically dumps the current canvas to a PNG so the live view can be
    followed from outside the GUI (remote debugging / pair tuning)."""
    global _last_snapshot
    if not _SNAPSHOT_PATH:
        return
    now = time.monotonic()
    if now - _last_snapshot < _SNAPSHOT_INTERVAL:
        return
    _last_snapshot = now
    try:
        rgba = np.asarray(fig.canvas.buffer_rgba())
        tmp_path = _SNAPSHOT_PATH + '.tmp'
        plt.imsave(tmp_path, rgba, format='png')
        os.replace(tmp_path, _SNAPSHOT_PATH)  # atomic: readers never see partial files
    except Exception:
        pass  # snapshots are best-effort, never break the live view


def _on_key(event):
    global _color_mode
    if event.key in ('q', 'escape'):
        raise SystemExit("Visualizer exited by user.")
    if event.key == 'c':
        if _color_mode == 'doppler':
            _color_mode = 'height'
            _scatter.set_cmap('viridis')
            _scatter.set_clim(0.0, 2.5)
            _cbar.set_label('Height (m)', color='#cccccc')
        else:
            _color_mode = 'doppler'
            _scatter.set_cmap('coolwarm')
            _scatter.set_clim(-_DOPPLER_RANGE, _DOPPLER_RANGE)
            _cbar.set_label('Radial velocity (m/s)', color='#cccccc')
        fig.canvas.draw()  # full redraw updates the colorbar and re-caches the blit background


def _on_draw(event):
    # Fires on every full redraw (initial draw, resize): re-cache the background
    global _background
    _background = fig.canvas.copy_from_bbox(fig.bbox)


def start_visualizer():
    global fig, ax, _scatter, _count_text, _use_blit, _cbar

    fig_h = 8.0
    fig_w = fig_h * (x_range + 1) / (y_range + 1) + 1.5  # extra width for colorbar
    fig, ax = plt.subplots(figsize=(fig_w, fig_h))
    fig.patch.set_facecolor('#101418')
    ax.set_facecolor('#101418')
    ax.set_xlim(BOUNDARY_X_MIN - 0.5, BOUNDARY_X_MAX + 0.5)
    ax.set_ylim(BOUNDARY_Y_MIN - 0.5, BOUNDARY_Y_MAX + 0.5)
    ax.set_aspect('equal')
    ax.invert_yaxis()
    ax.invert_xaxis()
    ax.tick_params(colors='#aaaaaa')
    for spine in ax.spines.values():
        spine.set_color('#444444')
    ax.grid(True, color='#2a2f36', linewidth=0.6, zorder=0)
    ax.set_axisbelow(True)
    ax.set_xlabel('X (m)', color='#cccccc')
    ax.set_ylabel('Y (m)', color='#cccccc')
    ax.set_title('OccuPi — Live Sensor View', color='white', fontsize=13)

    # Sensor FOV overlay: a ceiling/overhead sensor sees a roughly circular
    # patch of floor under it (cone hitting the floor), while a wall sensor
    # fans out in an azimuth wedge. Pick the shape from the mounting tilt.
    sensor_h, sensor_tilt = _read_sensor_mount()
    if sensor_tilt > math.radians(60):  # boresight (mostly) downward -> overhead
        coverage_r = sensor_h * math.tan(math.radians(_FOV_DEG))
        ax.add_patch(patches.Circle(
            (0, 0), coverage_r, facecolor='cyan', alpha=0.04,
            edgecolor='cyan', linewidth=0.8, zorder=1
        ))
    else:
        ax.add_patch(patches.Wedge(
            (0, 0), BOUNDARY_Y_MAX, 90 - _FOV_DEG, 90 + _FOV_DEG,
            facecolor='cyan', alpha=0.04, edgecolor='none', zorder=1
        ))
    ax.scatter([0], [0], marker='s', s=80, c='orange', zorder=6)
    ax.text(0, -0.35, 'Sensor', color='orange', fontsize=9, ha='center', zorder=6)

    # Tracker boundary box (outer tracking limit)
    ax.add_patch(patches.Rectangle(
        (BOUNDARY_X_MIN, BOUNDARY_Y_MIN), x_range, y_range,
        linewidth=1.5, edgecolor='cyan', facecolor='none', zorder=2
    ))

    # Static boundary box from the chirp config (zone where static targets survive)
    static_box = _read_static_boundary()
    if static_box:
        sx_min, sx_max, sy_min, sy_max = static_box[:4]
        ax.add_patch(patches.Rectangle(
            (sx_min, sy_min), sx_max - sx_min, sy_max - sy_min,
            linewidth=1.2, edgecolor='#888888', facecolor='none',
            linestyle='--', zorder=2
        ))
        ax.text(sx_min, sy_min - 0.15, 'static zone', color='#888888',
                fontsize=8, ha='left', zorder=2)

    # Point cloud, colored by radial velocity (blue = approaching, red = receding)
    _scatter = ax.scatter(
        [], [], s=8, c=[], cmap='coolwarm',
        vmin=-_DOPPLER_RANGE, vmax=_DOPPLER_RANGE,
        alpha=0.75, zorder=3, edgecolors='none'
    )
    _cbar = fig.colorbar(_scatter, ax=ax, fraction=0.04, pad=0.02)
    _cbar.set_label('Radial velocity (m/s)', color='#cccccc')
    _cbar.ax.tick_params(colors='#aaaaaa')
    _cbar.outline.set_edgecolor('#444444')

    for _ in range(_MAX_TARGETS):
        c = patches.Circle((0, 0), radius=0.3,
                           linewidth=2, edgecolor='red', facecolor='none',
                           visible=False, zorder=5)
        ax.add_patch(c)
        _target_circles.append(c)

        lbl = ax.text(0, 0, '', color='red', fontsize=8, ha='center',
                      visible=False, zorder=6)
        _target_labels.append(lbl)

        trail, = ax.plot([], [], linewidth=1.5, alpha=0.45, visible=False, zorder=4)
        _trail_lines.append(trail)

        vel, = ax.plot([], [], linewidth=2, alpha=0.9, visible=False, zorder=5)
        _vel_lines.append(vel)

    _count_text = ax.text(
        0.02, 0.98, 'People: 0', transform=ax.transAxes,
        color='white', fontsize=12, va='top', zorder=6
    )
    ax.text(
        0.98, 0.98, "q/esc: quit · c: doppler/height", transform=ax.transAxes,
        color='#777777', fontsize=8, va='top', ha='right', zorder=6
    )

    # Draw order matters for blitting: scatter below trails below circles/labels
    _dynamic_artists.extend([_scatter, *_trail_lines, *_vel_lines,
                             *_target_circles, *_target_labels, _count_text])
    _use_blit = fig.canvas.supports_blit
    if _use_blit:
        for artist in _dynamic_artists:
            artist.set_animated(True)  # exclude from full redraws, drawn manually
        fig.canvas.mpl_connect('draw_event', _on_draw)

    fig.canvas.mpl_connect('key_press_event', _on_key)
    fig.tight_layout()
    plt.show(block=False)
    fig.canvas.draw()


def _read_sensor_mount():
    """Sensor height (m) and tilt (rad) from the chirp config, so the FOV
    overlay matches the actual mounting (wall = wedge, ceiling = floor disk)."""
    try:
        from sensor.receiver import SENSOR_HEIGHT_M, SENSOR_TILT_RAD
        return SENSOR_HEIGHT_M, SENSOR_TILT_RAD
    except Exception:
        return 0.0, 0.0


def _read_static_boundary():
    """Parses staticBoundaryBox from the chirp config so the plot stays in sync with the sensor."""
    try:
        from sensor.receiver import CONFIG_FILE
        with open(CONFIG_FILE) as f:
            for line in f:
                if line.startswith('staticBoundaryBox'):
                    return [float(v) for v in line.split()[1:]]
    except Exception:
        pass
    return None
