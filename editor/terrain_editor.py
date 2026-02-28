#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
地形类型编辑器
"""

import tkinter as tk
from tkinter import ttk, messagebox, colorchooser
from csv_utils import read_csv, write_csv, get_fieldnames


class TerrainEditor:
    def __init__(self, parent):
        self.frame = ttk.Frame(parent)
        self.current_terrain = None

        self.create_widgets()
        self.load_data()

    def create_widgets(self):
        """创建界面组件"""
        # 左侧 - 地形列表
        left_panel = ttk.Frame(self.frame, width=200)
        left_panel.pack(side=tk.LEFT, fill=tk.Y, padx=5, pady=5)

        ttk.Label(left_panel, text="地形类型列表").pack()

        list_frame = ttk.Frame(left_panel)
        list_frame.pack(fill=tk.BOTH, expand=True)

        self.terrain_listbox = tk.Listbox(list_frame, width=25)
        self.terrain_listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.terrain_listbox.bind('<<ListboxSelect>>', self.on_terrain_select)

        scrollbar = ttk.Scrollbar(list_frame, orient=tk.VERTICAL,
                                  command=self.terrain_listbox.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.terrain_listbox.config(yscrollcommand=scrollbar.set)

        btn_frame = ttk.Frame(left_panel)
        btn_frame.pack(fill=tk.X, pady=5)

        ttk.Button(btn_frame, text="新建地形", command=self.new_terrain).pack(fill=tk.X)
        ttk.Button(btn_frame, text="删除地形", command=self.delete_terrain).pack(fill=tk.X)
        ttk.Button(btn_frame, text="保存所有", command=self.save_all).pack(fill=tk.X)

        # 右侧 - 编辑面板
        right_panel = ttk.Frame(self.frame)
        right_panel.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=5, pady=5)

        self.fields = {}
        labels = [
            ('id', '地形ID'),
            ('name', '中文名称'),
            ('icon', '图标'),
        ]

        for field, label in labels:
            row = ttk.Frame(right_panel)
            row.pack(fill=tk.X, padx=10, pady=2)
            ttk.Label(row, text=label, width=15).pack(side=tk.LEFT)
            entry = ttk.Entry(row)
            entry.pack(side=tk.LEFT, fill=tk.X, expand=True)
            self.fields[field] = entry

        # 可通行性
        passable_row = ttk.Frame(right_panel)
        passable_row.pack(fill=tk.X, padx=10, pady=2)
        ttk.Label(passable_row, text="可通行", width=15).pack(side=tk.LEFT)
        self.passable_var = tk.BooleanVar(value=True)
        ttk.Checkbutton(passable_row, variable=self.passable_var).pack(side=tk.LEFT)

        # 颜色
        color_row = ttk.Frame(right_panel)
        color_row.pack(fill=tk.X, padx=10, pady=2)
        ttk.Label(color_row, text="颜色", width=15).pack(side=tk.LEFT)
        self.color_entry = ttk.Entry(color_row)
        self.color_entry.pack(side=tk.LEFT, fill=tk.X, expand=True)
        self.color_preview = tk.Canvas(color_row, width=30, height=24, bg='#FFFFFF',
                                       relief='sunken', borderwidth=1)
        self.color_preview.pack(side=tk.LEFT, padx=(5, 0))
        self.color_preview.bind('<Button-1>', self.pick_color)
        self.color_entry.bind('<KeyRelease>', self.on_color_entry_change)

    def load_data(self):
        """加载数据"""
        self.terrains = read_csv('terrain_types.csv')
        self.refresh_terrain_list()

    def refresh_terrain_list(self):
        """刷新地形列表"""
        self.terrain_listbox.delete(0, tk.END)
        for t in self.terrains:
            self.terrain_listbox.insert(tk.END, f"{t['id']} - {t.get('name', '')}")

    def on_terrain_select(self, event):
        """选择地形"""
        selection = self.terrain_listbox.curselection()
        if selection:
            self.save_current_terrain()
            idx = selection[0]
            self.current_terrain = self.terrains[idx]
            self.load_terrain_data()

    def load_terrain_data(self):
        """加载地形数据到表单"""
        if not self.current_terrain:
            return

        for field, entry in self.fields.items():
            entry.delete(0, tk.END)
            entry.insert(0, self.current_terrain.get(field, ''))

        self.passable_var.set(self.current_terrain.get('passable', 'true').lower() == 'true')

        color = self.current_terrain.get('color', '#FFFFFF')
        self.color_entry.delete(0, tk.END)
        self.color_entry.insert(0, color)
        self.update_color_preview(color)

    def save_current_terrain(self):
        """保存当前地形到内存"""
        if not self.current_terrain:
            return

        for field, entry in self.fields.items():
            self.current_terrain[field] = entry.get()

        self.current_terrain['passable'] = str(self.passable_var.get()).lower()
        self.current_terrain['color'] = self.color_entry.get()
        self.refresh_terrain_list()

    def pick_color(self, event=None):
        """打开颜色选择器"""
        current = self.color_entry.get() or '#FFFFFF'
        result = colorchooser.askcolor(color=current, title="选择地形颜色")
        if result[1]:
            self.color_entry.delete(0, tk.END)
            self.color_entry.insert(0, result[1])
            self.update_color_preview(result[1])

    def on_color_entry_change(self, event=None):
        """颜色输入框变化时更新预览"""
        color = self.color_entry.get()
        self.update_color_preview(color)

    def update_color_preview(self, color):
        """更新颜色预览"""
        try:
            self.color_preview.config(bg=color)
        except tk.TclError:
            pass

    def new_terrain(self):
        """新建地形"""
        dialog = tk.Toplevel(self.frame)
        dialog.title("新建地形")
        dialog.geometry("300x150")

        ttk.Label(dialog, text="地形ID:").pack(pady=5)
        id_entry = ttk.Entry(dialog)
        id_entry.pack(fill=tk.X, padx=20)

        ttk.Label(dialog, text="中文名称:").pack(pady=5)
        name_entry = ttk.Entry(dialog)
        name_entry.pack(fill=tk.X, padx=20)

        def create():
            terrain_id = id_entry.get().strip().upper()
            if not terrain_id:
                messagebox.showerror("错误", "请输入地形ID")
                return

            for t in self.terrains:
                if t['id'] == terrain_id:
                    messagebox.showerror("错误", "地形ID已存在")
                    return

            self.terrains.append({
                'id': terrain_id,
                'name': name_entry.get(),
                'icon': '',
                'passable': 'true',
                'color': '#FFFFFF'
            })
            self.refresh_terrain_list()
            dialog.destroy()

        ttk.Button(dialog, text="创建", command=create).pack(pady=10)

    def delete_terrain(self):
        """删除地形"""
        selection = self.terrain_listbox.curselection()
        if not selection:
            messagebox.showwarning("警告", "请先选择一个地形")
            return

        idx = selection[0]
        terrain = self.terrains[idx]

        if not messagebox.askyesno("确认", f"确定要删除地形 {terrain['id']} 吗？"):
            return

        del self.terrains[idx]
        self.current_terrain = None
        self.refresh_terrain_list()

    def save_all(self):
        """保存所有数据"""
        self.save_current_terrain()

        fieldnames = get_fieldnames('terrain_types.csv')
        if not fieldnames:
            fieldnames = ['id', 'name', 'icon', 'passable', 'color']
        write_csv('terrain_types.csv', self.terrains, fieldnames)

        messagebox.showinfo("提示", "地形数据已保存")
