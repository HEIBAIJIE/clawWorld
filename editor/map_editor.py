#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
地图编辑器
"""

import tkinter as tk
from tkinter import ttk, messagebox, simpledialog
from csv_utils import read_csv, write_csv, get_fieldnames
from constants import (TERRAIN_TYPES, TERRAIN_COLORS, ENTITY_TYPES,
                       ENTITY_COLORS, PASSABLE_TERRAINS)


class MapEditor:
    def __init__(self, parent):
        self.frame = ttk.Frame(parent)
        self.current_map = None
        self.cell_size = 30
        self.current_tool = 'terrain'
        self.current_terrain = 'GRASS'
        self.current_entity_type = 'WAYPOINT'

        self.create_widgets()
        self.load_data()

    def create_widgets(self):
        """创建界面组件"""
        # 左侧面板 - 地图列表
        left_panel = ttk.Frame(self.frame, width=200)
        left_panel.pack(side=tk.LEFT, fill=tk.Y, padx=5, pady=5)

        ttk.Label(left_panel, text="地图列表").pack()

        # 地图列表框
        list_frame = ttk.Frame(left_panel)
        list_frame.pack(fill=tk.BOTH, expand=True)

        self.map_listbox = tk.Listbox(list_frame, width=25)
        self.map_listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.map_listbox.bind('<<ListboxSelect>>', self.on_map_select)

        scrollbar = ttk.Scrollbar(list_frame, orient=tk.VERTICAL,
                                  command=self.map_listbox.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.map_listbox.config(yscrollcommand=scrollbar.set)

        # 地图操作按钮
        btn_frame = ttk.Frame(left_panel)
        btn_frame.pack(fill=tk.X, pady=5)

        ttk.Button(btn_frame, text="新建地图", command=self.new_map).pack(fill=tk.X)
        ttk.Button(btn_frame, text="删除地图", command=self.delete_map).pack(fill=tk.X)
        ttk.Button(btn_frame, text="编辑属性", command=self.edit_map_properties).pack(fill=tk.X)

        # 中间面板 - 地图画布
        center_panel = ttk.Frame(self.frame)
        center_panel.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=5, pady=5)

        # 画布容器（带滚动条）
        canvas_frame = ttk.Frame(center_panel)
        canvas_frame.pack(fill=tk.BOTH, expand=True)

        self.canvas = tk.Canvas(canvas_frame, bg='white')
        self.canvas.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        v_scroll = ttk.Scrollbar(canvas_frame, orient=tk.VERTICAL,
                                 command=self.canvas.yview)
        v_scroll.pack(side=tk.RIGHT, fill=tk.Y)

        h_scroll = ttk.Scrollbar(center_panel, orient=tk.HORIZONTAL,
                                 command=self.canvas.xview)
        h_scroll.pack(fill=tk.X)

        self.canvas.config(xscrollcommand=h_scroll.set, yscrollcommand=v_scroll.set)
        self.canvas.bind('<Button-1>', self.on_canvas_click)
        self.canvas.bind('<B1-Motion>', self.on_canvas_drag)
        self.canvas.bind('<Button-3>', self.on_canvas_right_click)  # 右键查看信息

        # 右侧面板 - 工具栏
        right_panel = ttk.Frame(self.frame, width=200)
        right_panel.pack(side=tk.RIGHT, fill=tk.Y, padx=5, pady=5)

        self.create_tool_panel(right_panel)

    def create_tool_panel(self, parent):
        """创建工具面板"""
        # 工具选择
        ttk.Label(parent, text="编辑工具").pack(pady=5)

        self.tool_var = tk.StringVar(value='terrain')
        ttk.Radiobutton(parent, text="地形", variable=self.tool_var,
                        value='terrain', command=self.on_tool_change).pack(anchor=tk.W)
        ttk.Radiobutton(parent, text="实体", variable=self.tool_var,
                        value='entity', command=self.on_tool_change).pack(anchor=tk.W)

        ttk.Separator(parent, orient=tk.HORIZONTAL).pack(fill=tk.X, pady=10)

        # 地形选择
        ttk.Label(parent, text="地形类型").pack(pady=5)
        self.terrain_var = tk.StringVar(value='GRASS')
        self.terrain_combo = ttk.Combobox(parent, textvariable=self.terrain_var,
                                          values=TERRAIN_TYPES, state='readonly')
        self.terrain_combo.pack(fill=tk.X)
        self.terrain_combo.bind('<<ComboboxSelected>>', self.on_terrain_change)

        # 地形颜色预览
        self.terrain_preview = tk.Canvas(parent, width=50, height=30, bg='#90EE90')
        self.terrain_preview.pack(pady=5)

        ttk.Separator(parent, orient=tk.HORIZONTAL).pack(fill=tk.X, pady=10)

        # 实体选择
        ttk.Label(parent, text="实体类型").pack(pady=5)
        self.entity_type_var = tk.StringVar(value='WAYPOINT')
        self.entity_combo = ttk.Combobox(parent, textvariable=self.entity_type_var,
                                         values=ENTITY_TYPES, state='readonly')
        self.entity_combo.pack(fill=tk.X)

        # 实体ID选择
        ttk.Label(parent, text="实体ID").pack(pady=5)
        self.entity_id_var = tk.StringVar()
        self.entity_id_combo = ttk.Combobox(parent, textvariable=self.entity_id_var)
        self.entity_id_combo.pack(fill=tk.X)
        self.entity_combo.bind('<<ComboboxSelected>>', self.on_entity_type_change)

        ttk.Separator(parent, orient=tk.HORIZONTAL).pack(fill=tk.X, pady=10)

        # 保存按钮
        ttk.Button(parent, text="保存地图", command=self.save_current_map).pack(fill=tk.X, pady=5)

        ttk.Separator(parent, orient=tk.HORIZONTAL).pack(fill=tk.X, pady=10)

        # 格子信息面板
        ttk.Label(parent, text="格子信息").pack(pady=5)
        self.cell_info_label = ttk.Label(parent, text="坐标: -")
        self.cell_info_label.pack(anchor=tk.W)
        self.terrain_info_label = ttk.Label(parent, text="地形: -")
        self.terrain_info_label.pack(anchor=tk.W)

        ttk.Label(parent, text="实体列表:").pack(anchor=tk.W, pady=(5, 0))

        # 实体列表框
        entity_list_frame = ttk.Frame(parent)
        entity_list_frame.pack(fill=tk.BOTH, expand=True)

        self.entity_listbox = tk.Listbox(entity_list_frame, height=6)
        self.entity_listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.entity_listbox.bind('<Double-Button-1>', self.on_entity_double_click)

        entity_scrollbar = ttk.Scrollbar(entity_list_frame, orient=tk.VERTICAL,
                                         command=self.entity_listbox.yview)
        entity_scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.entity_listbox.config(yscrollcommand=entity_scrollbar.set)

        # 提示
        ttk.Label(parent, text="(双击编辑实体)", font=('Arial', 8)).pack(anchor=tk.W)

        # 删除按钮
        btn_frame = ttk.Frame(parent)
        btn_frame.pack(fill=tk.X, pady=5)
        ttk.Button(btn_frame, text="删除选中", command=self.delete_selected_entity).pack(side=tk.LEFT, padx=2)
        ttk.Button(btn_frame, text="删除全部", command=self.delete_all_entities_at_cell).pack(side=tk.LEFT, padx=2)

        ttk.Separator(parent, orient=tk.HORIZONTAL).pack(fill=tk.X, pady=10)

        # 图例
        ttk.Label(parent, text="图例").pack(pady=5)
        legend_frame = ttk.Frame(parent)
        legend_frame.pack(fill=tk.X)

        for entity_type, color in ENTITY_COLORS.items():
            row = ttk.Frame(legend_frame)
            row.pack(fill=tk.X)
            canvas = tk.Canvas(row, width=15, height=15)
            canvas.pack(side=tk.LEFT, padx=2)
            canvas.create_oval(2, 2, 13, 13, fill=color, outline='black')
            ttk.Label(row, text=entity_type).pack(side=tk.LEFT)

        # 初始化当前选中格子
        self.selected_cell = None
        self.selected_entities = []

    def load_data(self):
        """加载所有数据"""
        self.maps = read_csv('maps.csv')
        self.terrains = read_csv('map_terrain.csv')
        self.entities = read_csv('map_entities.csv')
        self.waypoints = read_csv('waypoints.csv')
        self.npcs = read_csv('npcs.csv')
        self.enemies = read_csv('enemies.csv')

        self.refresh_map_list()
        self.refresh_entity_ids()

    def refresh_map_list(self):
        """刷新地图列表"""
        self.map_listbox.delete(0, tk.END)
        for m in self.maps:
            self.map_listbox.insert(tk.END, f"{m['id']} - {m['name']}")

    def refresh_entity_ids(self):
        """刷新实体ID列表"""
        entity_type = self.entity_type_var.get()
        ids = []
        if entity_type == 'WAYPOINT':
            ids = [w['id'] for w in self.waypoints]
        elif entity_type == 'NPC':
            ids = [n['id'] for n in self.npcs]
        elif entity_type == 'ENEMY':
            ids = [e['id'] for e in self.enemies]
        elif entity_type == 'CAMPFIRE':
            ids = ['campfire']
        self.entity_id_combo['values'] = ids
        if ids:
            self.entity_id_var.set(ids[0])

    def on_map_select(self, event):
        """选择地图"""
        selection = self.map_listbox.curselection()
        if selection:
            idx = selection[0]
            self.current_map = self.maps[idx]
            self.draw_map()

    def on_tool_change(self):
        """工具切换"""
        self.current_tool = self.tool_var.get()

    def on_terrain_change(self, event):
        """地形切换"""
        terrain = self.terrain_var.get()
        self.current_terrain = terrain
        color = TERRAIN_COLORS.get(terrain, '#FFFFFF')
        self.terrain_preview.config(bg=color)

    def on_entity_type_change(self, event):
        """实体类型切换"""
        self.refresh_entity_ids()

    def draw_map(self):
        """绘制地图"""
        if not self.current_map:
            return

        self.canvas.delete('all')
        map_id = self.current_map['id']
        width = int(self.current_map['width'])
        height = int(self.current_map['height'])
        default_terrain = self.current_map.get('defaultTerrain', 'GRASS')

        # 设置画布大小
        canvas_width = width * self.cell_size
        canvas_height = height * self.cell_size
        self.canvas.config(scrollregion=(0, 0, canvas_width, canvas_height))

        # 初始化地形网格
        self.terrain_grid = [[default_terrain for _ in range(width)] for _ in range(height)]

        # 加载地形数据
        for t in self.terrains:
            if t['mapId'] == map_id:
                x1, y1 = int(t['x1']), int(t['y1'])
                x2, y2 = int(t['x2']), int(t['y2'])
                terrain_types = t['terrainTypes'].split(',')
                terrain = terrain_types[0] if terrain_types else default_terrain
                for y in range(y1, y2 + 1):
                    for x in range(x1, x2 + 1):
                        if 0 <= x < width and 0 <= y < height:
                            self.terrain_grid[y][x] = terrain

        # 绘制地形格子（Y轴翻转，0在底部）
        for y in range(height):
            for x in range(width):
                terrain = self.terrain_grid[y][x]
                color = TERRAIN_COLORS.get(terrain, '#FFFFFF')
                # Y轴翻转
                draw_y = (height - 1 - y) * self.cell_size
                draw_x = x * self.cell_size
                self.canvas.create_rectangle(
                    draw_x, draw_y,
                    draw_x + self.cell_size, draw_y + self.cell_size,
                    fill=color, outline='gray', tags=f'cell_{x}_{y}'
                )

        # 绘制实体（支持叠加显示）
        # 先按位置分组
        entity_groups = {}
        for e in self.entities:
            if e['mapId'] == map_id:
                pos = (int(e['x']), int(e['y']))
                if pos not in entity_groups:
                    entity_groups[pos] = []
                entity_groups[pos].append(e)

        # 绘制每个位置的实体
        for (x, y), entities_at_pos in entity_groups.items():
            count = len(entities_at_pos)
            # Y轴翻转
            base_draw_y = (height - 1 - y) * self.cell_size + self.cell_size // 2
            base_draw_x = x * self.cell_size + self.cell_size // 2

            if count == 1:
                # 单个实体，居中显示
                e = entities_at_pos[0]
                entity_type = e['entityType']
                color = ENTITY_COLORS.get(entity_type, '#FFFFFF')
                r = self.cell_size // 3
                self.canvas.create_oval(
                    base_draw_x - r, base_draw_y - r, base_draw_x + r, base_draw_y + r,
                    fill=color, outline='black', tags=f'entity_{x}_{y}'
                )
            else:
                # 多个实体，按网格排列
                r = self.cell_size // 4  # 缩小半径
                # 计算排列方式
                if count == 2:
                    offsets = [(-r//2, 0), (r//2, 0)]
                elif count == 3:
                    offsets = [(-r//2, -r//2), (r//2, -r//2), (0, r//2)]
                elif count == 4:
                    offsets = [(-r//2, -r//2), (r//2, -r//2), (-r//2, r//2), (r//2, r//2)]
                else:
                    # 超过4个，环形排列
                    import math
                    offsets = []
                    for i in range(count):
                        angle = 2 * math.pi * i / count
                        ox = int(r * 0.8 * math.cos(angle))
                        oy = int(r * 0.8 * math.sin(angle))
                        offsets.append((ox, oy))

                for i, e in enumerate(entities_at_pos):
                    entity_type = e['entityType']
                    color = ENTITY_COLORS.get(entity_type, '#FFFFFF')
                    ox, oy = offsets[i] if i < len(offsets) else (0, 0)
                    draw_x = base_draw_x + ox
                    draw_y = base_draw_y + oy
                    small_r = r // 2 if count > 2 else r * 2 // 3
                    self.canvas.create_oval(
                        draw_x - small_r, draw_y - small_r, draw_x + small_r, draw_y + small_r,
                        fill=color, outline='black', tags=f'entity_{x}_{y}_{i}'
                    )

        # 绘制坐标
        for x in range(width):
            self.canvas.create_text(
                x * self.cell_size + self.cell_size // 2, canvas_height + 10,
                text=str(x), font=('Arial', 8)
            )
        for y in range(height):
            draw_y = (height - 1 - y) * self.cell_size + self.cell_size // 2
            self.canvas.create_text(
                canvas_width + 15, draw_y, text=str(y), font=('Arial', 8)
            )

        # 扩展滚动区域以显示坐标
        self.canvas.config(scrollregion=(0, 0, canvas_width + 30, canvas_height + 20))

    def on_canvas_click(self, event):
        """画布点击事件"""
        self.handle_canvas_action(event)

    def on_canvas_drag(self, event):
        """画布拖拽事件"""
        if self.current_tool == 'terrain':
            self.handle_canvas_action(event)

    def on_canvas_right_click(self, event):
        """右键点击查看格子信息"""
        self.update_cell_info(event)

    def get_cell_coords(self, event):
        """从事件获取格子坐标"""
        if not self.current_map:
            return None, None

        canvas_x = self.canvas.canvasx(event.x)
        canvas_y = self.canvas.canvasy(event.y)

        width = int(self.current_map['width'])
        height = int(self.current_map['height'])

        x = int(canvas_x // self.cell_size)
        y = height - 1 - int(canvas_y // self.cell_size)

        if not (0 <= x < width and 0 <= y < height):
            return None, None

        return x, y

    def update_cell_info(self, event):
        """更新格子信息面板"""
        x, y = self.get_cell_coords(event)
        if x is None:
            return

        map_id = self.current_map['id']
        self.selected_cell = (x, y)

        # 更新坐标显示
        self.cell_info_label.config(text=f"坐标: ({x}, {y})")

        # 获取地形
        terrain = self.terrain_grid[y][x] if hasattr(self, 'terrain_grid') else '-'
        self.terrain_info_label.config(text=f"地形: {terrain}")

        # 获取该位置的实体
        self.selected_entities = []
        self.entity_listbox.delete(0, tk.END)

        for i, e in enumerate(self.entities):
            if e['mapId'] == map_id and int(e['x']) == x and int(e['y']) == y:
                self.selected_entities.append(i)
                self.entity_listbox.insert(tk.END, f"{e['entityType']} - {e['entityId']}")

    def delete_selected_entity(self):
        """删除选中的实体"""
        selection = self.entity_listbox.curselection()
        if not selection:
            messagebox.showwarning("警告", "请先选择要删除的实体")
            return

        idx = self.selected_entities[selection[0]]
        del self.entities[idx]
        self.draw_map()

        # 刷新信息面板
        if self.selected_cell:
            # 模拟事件来刷新
            self.refresh_cell_info()

    def delete_all_entities_at_cell(self):
        """删除当前格子的所有实体"""
        if not self.selected_entities:
            messagebox.showwarning("警告", "当前格子没有实体")
            return

        if not messagebox.askyesno("确认", f"确定要删除该位置的所有 {len(self.selected_entities)} 个实体吗？"):
            return

        # 从后往前删除
        for idx in sorted(self.selected_entities, reverse=True):
            del self.entities[idx]

        self.draw_map()
        self.refresh_cell_info()

    def refresh_cell_info(self):
        """刷新当前选中格子的信息"""
        if not self.selected_cell or not self.current_map:
            return

        x, y = self.selected_cell
        map_id = self.current_map['id']

        self.selected_entities = []
        self.entity_listbox.delete(0, tk.END)

        for i, e in enumerate(self.entities):
            if e['mapId'] == map_id and int(e['x']) == x and int(e['y']) == y:
                self.selected_entities.append(i)
                self.entity_listbox.insert(tk.END, f"{e['entityType']} - {e['entityId']}")

    def on_entity_double_click(self, event):
        """双击实体列表项查看/编辑实体信息"""
        selection = self.entity_listbox.curselection()
        if not selection:
            return

        idx = self.selected_entities[selection[0]]
        entity = self.entities[idx]
        entity_type = entity['entityType']
        entity_id = entity['entityId']

        if entity_type == 'WAYPOINT':
            self.edit_waypoint_connections(entity_id)
        else:
            # 显示实体详情
            self.show_entity_info(entity)

    def show_entity_info(self, entity):
        """显示实体详细信息"""
        dialog = tk.Toplevel(self.frame)
        dialog.title(f"实体信息 - {entity['entityType']}")
        dialog.geometry("300x200")

        info_frame = ttk.Frame(dialog, padding=10)
        info_frame.pack(fill=tk.BOTH, expand=True)

        ttk.Label(info_frame, text=f"类型: {entity['entityType']}").pack(anchor=tk.W)
        ttk.Label(info_frame, text=f"ID: {entity['entityId']}").pack(anchor=tk.W)
        ttk.Label(info_frame, text=f"位置: ({entity['x']}, {entity['y']})").pack(anchor=tk.W)
        if entity.get('instanceId'):
            ttk.Label(info_frame, text=f"实例ID: {entity['instanceId']}").pack(anchor=tk.W)

        # 显示额外信息
        if entity['entityType'] == 'ENEMY':
            for e in self.enemies:
                if e['id'] == entity['entityId']:
                    ttk.Separator(info_frame).pack(fill=tk.X, pady=5)
                    ttk.Label(info_frame, text=f"名称: {e.get('name', '-')}").pack(anchor=tk.W)
                    ttk.Label(info_frame, text=f"等级: {e.get('level', '-')}").pack(anchor=tk.W)
                    break
        elif entity['entityType'] == 'NPC':
            for n in self.npcs:
                if n['id'] == entity['entityId']:
                    ttk.Separator(info_frame).pack(fill=tk.X, pady=5)
                    ttk.Label(info_frame, text=f"名称: {n.get('name', '-')}").pack(anchor=tk.W)
                    break

        ttk.Button(dialog, text="关闭", command=dialog.destroy).pack(pady=10)

    def handle_canvas_action(self, event):
        """处理画布操作"""
        if not self.current_map:
            return

        x, y = self.get_cell_coords(event)
        if x is None:
            return

        if self.current_tool == 'terrain':
            self.set_terrain(x, y)
        elif self.current_tool == 'entity':
            self.add_entity(x, y)

    def set_terrain(self, x, y):
        """设置地形"""
        terrain = self.terrain_var.get()
        map_id = self.current_map['id']

        # 查找并更新或创建地形记录
        found = False
        for t in self.terrains:
            if (t['mapId'] == map_id and
                int(t['x1']) == x and int(t['y1']) == y and
                int(t['x2']) == x and int(t['y2']) == y):
                t['terrainTypes'] = terrain
                found = True
                break

        if not found:
            self.terrains.append({
                'mapId': map_id,
                'x1': str(x), 'y1': str(y),
                'x2': str(x), 'y2': str(y),
                'terrainTypes': terrain
            })

        self.draw_map()

    def add_entity(self, x, y):
        """添加实体"""
        map_id = self.current_map['id']
        entity_type = self.entity_type_var.get()
        entity_id = self.entity_id_var.get()

        if not entity_id:
            messagebox.showwarning("警告", "请选择实体ID")
            return

        # 生成实例ID（为同位置的相同实体生成唯一ID）
        existing_count = sum(1 for e in self.entities
                            if e['mapId'] == map_id and int(e['x']) == x and int(e['y']) == y
                            and e['entityType'] == entity_type and e['entityId'] == entity_id)
        instance_id = f"{entity_id}_{x}_{y}_{existing_count}" if entity_type == 'ENEMY' else ''

        self.entities.append({
            'mapId': map_id,
            'x': str(x), 'y': str(y),
            'entityType': entity_type,
            'entityId': entity_id,
            'instanceId': instance_id
        })

        self.draw_map()

    def edit_waypoint_connections(self, waypoint_id):
        """编辑传送点连接"""
        # 查找传送点
        waypoint = None
        for w in self.waypoints:
            if w['id'] == waypoint_id:
                waypoint = w
                break

        if not waypoint:
            messagebox.showerror("错误", "找不到传送点")
            return

        # 创建编辑对话框
        dialog = tk.Toplevel(self.frame)
        dialog.title(f"编辑传送点连接 - {waypoint_id}")
        dialog.geometry("400x300")

        ttk.Label(dialog, text="选择可传送到的地图传送点:").pack(pady=5)

        # 获取当前连接
        current_connections = waypoint.get('connectedWaypointIds', '').split(';')
        current_connections = [c for c in current_connections if c]

        # 创建复选框列表
        check_vars = {}
        frame = ttk.Frame(dialog)
        frame.pack(fill=tk.BOTH, expand=True, padx=10)

        canvas = tk.Canvas(frame)
        scrollbar = ttk.Scrollbar(frame, orient=tk.VERTICAL, command=canvas.yview)
        scrollable_frame = ttk.Frame(canvas)

        scrollable_frame.bind(
            "<Configure>",
            lambda e: canvas.configure(scrollregion=canvas.bbox("all"))
        )

        canvas.create_window((0, 0), window=scrollable_frame, anchor="nw")
        canvas.configure(yscrollcommand=scrollbar.set)

        for w in self.waypoints:
            if w['id'] != waypoint_id:
                var = tk.BooleanVar(value=w['id'] in current_connections)
                check_vars[w['id']] = var
                map_name = self.get_map_name(w['mapId'])
                ttk.Checkbutton(
                    scrollable_frame,
                    text=f"{w['id']} ({w['name']} - {map_name})",
                    variable=var
                ).pack(anchor=tk.W)

        canvas.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)

        def save_connections():
            connections = [wid for wid, var in check_vars.items() if var.get()]
            waypoint['connectedWaypointIds'] = ';'.join(connections)
            dialog.destroy()

        ttk.Button(dialog, text="保存", command=save_connections).pack(pady=10)

    def get_map_name(self, map_id):
        """获取地图名称"""
        for m in self.maps:
            if m['id'] == map_id:
                return m['name']
        return map_id

    def new_map(self):
        """新建地图"""
        dialog = tk.Toplevel(self.frame)
        dialog.title("新建地图")
        dialog.geometry("400x400")

        fields = {}
        labels = [
            ('id', '地图ID'),
            ('name', '地图名称'),
            ('description', '描述'),
            ('width', '宽度'),
            ('height', '高度'),
            ('recommendedLevel', '推荐等级')
        ]

        for field, label in labels:
            row = ttk.Frame(dialog)
            row.pack(fill=tk.X, padx=10, pady=2)
            ttk.Label(row, text=label, width=12).pack(side=tk.LEFT)
            entry = ttk.Entry(row)
            entry.pack(side=tk.LEFT, fill=tk.X, expand=True)
            fields[field] = entry

        # 默认值
        fields['width'].insert(0, '10')
        fields['height'].insert(0, '10')
        fields['recommendedLevel'].insert(0, '1')

        # 是否安全区
        safe_var = tk.BooleanVar(value=True)
        ttk.Checkbutton(dialog, text="安全区域", variable=safe_var).pack(pady=5)

        # 默认地形
        terrain_row = ttk.Frame(dialog)
        terrain_row.pack(fill=tk.X, padx=10, pady=2)
        ttk.Label(terrain_row, text="默认地形", width=12).pack(side=tk.LEFT)
        terrain_var = tk.StringVar(value='GRASS')
        terrain_combo = ttk.Combobox(terrain_row, textvariable=terrain_var,
                                     values=TERRAIN_TYPES, state='readonly')
        terrain_combo.pack(side=tk.LEFT, fill=tk.X, expand=True)

        def create_map():
            map_id = fields['id'].get().strip()
            if not map_id:
                messagebox.showerror("错误", "请输入地图ID")
                return

            # 检查ID是否已存在
            for m in self.maps:
                if m['id'] == map_id:
                    messagebox.showerror("错误", "地图ID已存在")
                    return

            new_map_data = {
                'id': map_id,
                'name': fields['name'].get(),
                'description': fields['description'].get(),
                'width': fields['width'].get(),
                'height': fields['height'].get(),
                'isSafe': str(safe_var.get()).lower(),
                'recommendedLevel': fields['recommendedLevel'].get(),
                'defaultTerrain': terrain_var.get()
            }

            self.maps.append(new_map_data)
            self.refresh_map_list()
            dialog.destroy()

        ttk.Button(dialog, text="创建", command=create_map).pack(pady=10)

    def delete_map(self):
        """删除地图"""
        selection = self.map_listbox.curselection()
        if not selection:
            messagebox.showwarning("警告", "请先选择一个地图")
            return

        idx = selection[0]
        map_data = self.maps[idx]
        map_id = map_data['id']

        if not messagebox.askyesno("确认", f"确定要删除地图 {map_id} 吗？\n这将同时删除该地图的所有地形和实体数据。"):
            return

        # 删除地图
        del self.maps[idx]

        # 删除相关地形
        self.terrains = [t for t in self.terrains if t['mapId'] != map_id]

        # 删除相关实体
        self.entities = [e for e in self.entities if e['mapId'] != map_id]

        # 删除相关传送点
        self.waypoints = [w for w in self.waypoints if w['mapId'] != map_id]

        self.current_map = None
        self.canvas.delete('all')
        self.refresh_map_list()

    def edit_map_properties(self):
        """编辑地图属性"""
        if not self.current_map:
            messagebox.showwarning("警告", "请先选择一个地图")
            return

        dialog = tk.Toplevel(self.frame)
        dialog.title(f"编辑地图属性 - {self.current_map['id']}")
        dialog.geometry("400x400")

        fields = {}
        labels = [
            ('name', '地图名称'),
            ('description', '描述'),
            ('width', '宽度'),
            ('height', '高度'),
            ('recommendedLevel', '推荐等级')
        ]

        for field, label in labels:
            row = ttk.Frame(dialog)
            row.pack(fill=tk.X, padx=10, pady=2)
            ttk.Label(row, text=label, width=12).pack(side=tk.LEFT)
            entry = ttk.Entry(row)
            entry.pack(side=tk.LEFT, fill=tk.X, expand=True)
            entry.insert(0, self.current_map.get(field, ''))
            fields[field] = entry

        # 是否安全区
        safe_var = tk.BooleanVar(value=self.current_map.get('isSafe', 'true').lower() == 'true')
        ttk.Checkbutton(dialog, text="安全区域", variable=safe_var).pack(pady=5)

        # 默认地形
        terrain_row = ttk.Frame(dialog)
        terrain_row.pack(fill=tk.X, padx=10, pady=2)
        ttk.Label(terrain_row, text="默认地形", width=12).pack(side=tk.LEFT)
        terrain_var = tk.StringVar(value=self.current_map.get('defaultTerrain', 'GRASS'))
        terrain_combo = ttk.Combobox(terrain_row, textvariable=terrain_var,
                                     values=TERRAIN_TYPES, state='readonly')
        terrain_combo.pack(side=tk.LEFT, fill=tk.X, expand=True)

        def save_properties():
            for field, entry in fields.items():
                self.current_map[field] = entry.get()
            self.current_map['isSafe'] = str(safe_var.get()).lower()
            self.current_map['defaultTerrain'] = terrain_var.get()
            self.refresh_map_list()
            self.draw_map()
            dialog.destroy()

        ttk.Button(dialog, text="保存", command=save_properties).pack(pady=10)

    def save_current_map(self):
        """保存当前地图数据"""
        # 保存地图
        fieldnames = get_fieldnames('maps.csv')
        if not fieldnames:
            fieldnames = ['id', 'name', 'description', 'width', 'height',
                          'isSafe', 'recommendedLevel', 'defaultTerrain']
        write_csv('maps.csv', self.maps, fieldnames)

        # 保存地形
        fieldnames = get_fieldnames('map_terrain.csv')
        if not fieldnames:
            fieldnames = ['mapId', 'x1', 'y1', 'x2', 'y2', 'terrainTypes']
        write_csv('map_terrain.csv', self.terrains, fieldnames)

        # 保存实体
        fieldnames = get_fieldnames('map_entities.csv')
        if not fieldnames:
            fieldnames = ['mapId', 'x', 'y', 'entityType', 'entityId', 'instanceId']
        write_csv('map_entities.csv', self.entities, fieldnames)

        # 保存传送点
        fieldnames = get_fieldnames('waypoints.csv')
        if not fieldnames:
            fieldnames = ['id', 'mapId', 'name', 'description', 'x', 'y', 'connectedWaypointIds']
        write_csv('waypoints.csv', self.waypoints, fieldnames)

        messagebox.showinfo("提示", "地图数据已保存")
