#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
宝箱编辑器
"""

import tkinter as tk
from tkinter import ttk, messagebox
from csv_utils import read_csv, write_csv, get_fieldnames


class ChestEditor:
    def __init__(self, parent):
        self.frame = ttk.Frame(parent)
        self.current_chest = None

        self.create_widgets()
        self.load_data()

    def create_widgets(self):
        """创建界面组件"""
        # 左侧 - 宝箱列表
        left_panel = ttk.Frame(self.frame, width=200)
        left_panel.pack(side=tk.LEFT, fill=tk.Y, padx=5, pady=5)

        ttk.Label(left_panel, text="宝箱列表").pack()

        list_frame = ttk.Frame(left_panel)
        list_frame.pack(fill=tk.BOTH, expand=True)

        self.chest_listbox = tk.Listbox(list_frame, width=25)
        self.chest_listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.chest_listbox.bind('<<ListboxSelect>>', self.on_chest_select)

        scrollbar = ttk.Scrollbar(list_frame, orient=tk.VERTICAL,
                                  command=self.chest_listbox.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.chest_listbox.config(yscrollcommand=scrollbar.set)

        btn_frame = ttk.Frame(left_panel)
        btn_frame.pack(fill=tk.X, pady=5)

        ttk.Button(btn_frame, text="新建宝箱", command=self.new_chest).pack(fill=tk.X)
        ttk.Button(btn_frame, text="删除宝箱", command=self.delete_chest).pack(fill=tk.X)
        ttk.Button(btn_frame, text="保存所有", command=self.save_all).pack(fill=tk.X)

        # 右侧 - 编辑面板
        right_panel = ttk.Frame(self.frame)
        right_panel.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=5, pady=5)

        # 创建notebook
        self.notebook = ttk.Notebook(right_panel)
        self.notebook.pack(fill=tk.BOTH, expand=True)

        self.create_basic_tab()
        self.create_loot_tab()

    def create_basic_tab(self):
        """创建基本信息标签页"""
        tab = ttk.Frame(self.notebook)
        self.notebook.add(tab, text="基本信息")

        self.basic_fields = {}
        labels = [
            ('id', '宝箱ID'),
            ('name', '名称'),
            ('description', '描述'),
            ('respawnSeconds', '刷新时间(秒)'),
            ('icon', '图标')
        ]

        for field, label in labels:
            row = ttk.Frame(tab)
            row.pack(fill=tk.X, padx=10, pady=2)
            ttk.Label(row, text=label, width=15).pack(side=tk.LEFT)
            entry = ttk.Entry(row)
            entry.pack(side=tk.LEFT, fill=tk.X, expand=True)
            self.basic_fields[field] = entry

        # 宝箱类型
        type_row = ttk.Frame(tab)
        type_row.pack(fill=tk.X, padx=10, pady=2)
        ttk.Label(type_row, text="宝箱类型", width=15).pack(side=tk.LEFT)
        self.type_var = tk.StringVar(value='SMALL')
        type_combo = ttk.Combobox(type_row, textvariable=self.type_var,
                                  values=['SMALL', 'LARGE'], state='readonly')
        type_combo.pack(side=tk.LEFT, fill=tk.X, expand=True)

        # 说明
        ttk.Label(tab, text="说明：").pack(anchor=tk.W, padx=10, pady=(10, 0))
        ttk.Label(tab, text="  SMALL - 小宝箱：每个玩家只能开一次，不影响其他玩家").pack(anchor=tk.W, padx=10)
        ttk.Label(tab, text="  LARGE - 大宝箱：所有玩家共享状态，开启后需等待刷新").pack(anchor=tk.W, padx=10)
        ttk.Label(tab, text="  刷新时间仅对大宝箱有效").pack(anchor=tk.W, padx=10)

    def create_loot_tab(self):
        """创建掉落物品标签页"""
        tab = ttk.Frame(self.notebook)
        self.notebook.add(tab, text="掉落物品")

        # 掉落物品列表
        list_frame = ttk.Frame(tab)
        list_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=5)

        columns = ('itemId', 'rarity', 'dropRate', 'minQty', 'maxQty')
        self.loot_tree = ttk.Treeview(list_frame, columns=columns, show='headings')
        self.loot_tree.heading('itemId', text='物品ID')
        self.loot_tree.heading('rarity', text='稀有度')
        self.loot_tree.heading('dropRate', text='掉落率')
        self.loot_tree.heading('minQty', text='最小数量')
        self.loot_tree.heading('maxQty', text='最大数量')
        self.loot_tree.column('itemId', width=120)
        self.loot_tree.column('rarity', width=80)
        self.loot_tree.column('dropRate', width=60)
        self.loot_tree.column('minQty', width=60)
        self.loot_tree.column('maxQty', width=60)
        self.loot_tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        scrollbar = ttk.Scrollbar(list_frame, orient=tk.VERTICAL,
                                  command=self.loot_tree.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.loot_tree.config(yscrollcommand=scrollbar.set)

        # 操作按钮
        btn_frame = ttk.Frame(tab)
        btn_frame.pack(fill=tk.X, padx=10, pady=5)

        ttk.Button(btn_frame, text="添加掉落", command=self.add_loot).pack(side=tk.LEFT, padx=2)
        ttk.Button(btn_frame, text="编辑掉落", command=self.edit_loot).pack(side=tk.LEFT, padx=2)
        ttk.Button(btn_frame, text="删除掉落", command=self.delete_loot).pack(side=tk.LEFT, padx=2)

    def load_data(self):
        """加载数据"""
        self.chests = read_csv('chests.csv')
        self.chest_loot = read_csv('chest_loot.csv')
        self.items = read_csv('items.csv')
        self.equipment = read_csv('equipment.csv')

        self.refresh_chest_list()

    def refresh_chest_list(self):
        """刷新宝箱列表"""
        self.chest_listbox.delete(0, tk.END)
        for chest in self.chests:
            type_label = '[小]' if chest.get('type') == 'SMALL' else '[大]'
            self.chest_listbox.insert(tk.END, f"{type_label} {chest['id']} - {chest['name']}")

    def on_chest_select(self, event):
        """选择宝箱"""
        selection = self.chest_listbox.curselection()
        if selection:
            idx = selection[0]
            self.current_chest = self.chests[idx]
            self.load_chest_data()

    def load_chest_data(self):
        """加载宝箱数据到表单"""
        if not self.current_chest:
            return

        for field, entry in self.basic_fields.items():
            entry.delete(0, tk.END)
            entry.insert(0, self.current_chest.get(field, ''))

        self.type_var.set(self.current_chest.get('type', 'SMALL'))

        self.refresh_loot_list()

    def refresh_loot_list(self):
        """刷新掉落物品列表"""
        for item in self.loot_tree.get_children():
            self.loot_tree.delete(item)

        if not self.current_chest:
            return

        chest_id = self.current_chest['id']
        for loot in self.chest_loot:
            if loot['chestId'] == chest_id:
                self.loot_tree.insert('', tk.END, values=(
                    loot['itemId'], loot['rarity'], loot['dropRate'],
                    loot['minQuantity'], loot['maxQuantity']
                ))

    def save_current_chest(self):
        """保存当前宝箱"""
        if not self.current_chest:
            return

        for field, entry in self.basic_fields.items():
            self.current_chest[field] = entry.get()

        self.current_chest['type'] = self.type_var.get()

        self.refresh_chest_list()

    def new_chest(self):
        """新建宝箱"""
        dialog = tk.Toplevel(self.frame)
        dialog.title("新建宝箱")
        dialog.geometry("350x250")

        ttk.Label(dialog, text="宝箱ID:").pack(pady=5)
        id_entry = ttk.Entry(dialog)
        id_entry.pack(fill=tk.X, padx=20)

        ttk.Label(dialog, text="宝箱名称:").pack(pady=5)
        name_entry = ttk.Entry(dialog)
        name_entry.pack(fill=tk.X, padx=20)

        ttk.Label(dialog, text="宝箱类型:").pack(pady=5)
        type_var = tk.StringVar(value='SMALL')
        type_combo = ttk.Combobox(dialog, textvariable=type_var,
                                  values=['SMALL', 'LARGE'], state='readonly')
        type_combo.pack(fill=tk.X, padx=20)

        ttk.Label(dialog, text="刷新时间(秒，仅大宝箱):").pack(pady=5)
        respawn_entry = ttk.Entry(dialog)
        respawn_entry.insert(0, '300')
        respawn_entry.pack(fill=tk.X, padx=20)

        def create():
            chest_id = id_entry.get().strip()
            if not chest_id:
                messagebox.showerror("错误", "请输入宝箱ID")
                return

            for chest in self.chests:
                if chest['id'] == chest_id:
                    messagebox.showerror("错误", "宝箱ID已存在")
                    return

            new_chest = {
                'id': chest_id,
                'name': name_entry.get(),
                'description': '一个宝箱',
                'type': type_var.get(),
                'respawnSeconds': respawn_entry.get() if type_var.get() == 'LARGE' else '0',
                'icon': ''
            }

            self.chests.append(new_chest)
            self.refresh_chest_list()
            dialog.destroy()

        ttk.Button(dialog, text="创建", command=create).pack(pady=10)

    def delete_chest(self):
        """删除宝箱"""
        selection = self.chest_listbox.curselection()
        if not selection:
            messagebox.showwarning("警告", "请先选择一个宝箱")
            return

        idx = selection[0]
        chest = self.chests[idx]

        if not messagebox.askyesno("确认", f"确定要删除宝箱 {chest['id']} 吗？"):
            return

        # 删除宝箱
        del self.chests[idx]

        # 删除相关掉落
        self.chest_loot = [l for l in self.chest_loot if l['chestId'] != chest['id']]

        self.current_chest = None
        self.refresh_chest_list()

    def add_loot(self):
        """添加掉落物品"""
        if not self.current_chest:
            messagebox.showwarning("警告", "请先选择一个宝箱")
            return

        self.edit_loot_dialog(None)

    def edit_loot(self):
        """编辑掉落物品"""
        if not self.current_chest:
            return

        selection = self.loot_tree.selection()
        if not selection:
            messagebox.showwarning("警告", "请先选择一个掉落物品")
            return

        item = self.loot_tree.item(selection[0])
        values = item['values']
        self.edit_loot_dialog(values)

    def edit_loot_dialog(self, existing_values):
        """掉落物品编辑对话框"""
        dialog = tk.Toplevel(self.frame)
        dialog.title("编辑掉落" if existing_values else "添加掉落")
        dialog.geometry("400x300")

        # 物品选择
        ttk.Label(dialog, text="物品:").pack(pady=5)
        item_var = tk.StringVar()
        all_items = [i['id'] for i in self.items] + [e['id'] for e in self.equipment]
        item_combo = ttk.Combobox(dialog, textvariable=item_var, values=all_items)
        item_combo.pack(fill=tk.X, padx=20)

        # 稀有度
        ttk.Label(dialog, text="稀有度:").pack(pady=5)
        rarity_var = tk.StringVar(value='COMMON')
        rarity_combo = ttk.Combobox(dialog, textvariable=rarity_var,
                                    values=['COMMON', 'EXCELLENT', 'RARE', 'EPIC', 'LEGENDARY', 'MYTHIC'],
                                    state='readonly')
        rarity_combo.pack(fill=tk.X, padx=20)

        # 掉落率
        ttk.Label(dialog, text="掉落率 (0.0-1.0):").pack(pady=5)
        rate_entry = ttk.Entry(dialog)
        rate_entry.insert(0, '0.5')
        rate_entry.pack(fill=tk.X, padx=20)

        # 数量范围
        qty_frame = ttk.Frame(dialog)
        qty_frame.pack(fill=tk.X, padx=20, pady=5)
        ttk.Label(qty_frame, text="数量范围:").pack(side=tk.LEFT)
        min_entry = ttk.Entry(qty_frame, width=8)
        min_entry.insert(0, '1')
        min_entry.pack(side=tk.LEFT, padx=5)
        ttk.Label(qty_frame, text="-").pack(side=tk.LEFT)
        max_entry = ttk.Entry(qty_frame, width=8)
        max_entry.insert(0, '1')
        max_entry.pack(side=tk.LEFT, padx=5)

        if existing_values:
            item_var.set(existing_values[0])
            rarity_var.set(existing_values[1])
            rate_entry.delete(0, tk.END)
            rate_entry.insert(0, existing_values[2])
            min_entry.delete(0, tk.END)
            min_entry.insert(0, existing_values[3])
            max_entry.delete(0, tk.END)
            max_entry.insert(0, existing_values[4])

        def save():
            item_id = item_var.get()
            if not item_id:
                messagebox.showerror("错误", "请选择物品")
                return

            chest_id = self.current_chest['id']

            # 如果是编辑，先删除旧的
            if existing_values:
                self.chest_loot = [l for l in self.chest_loot
                                   if not (l['chestId'] == chest_id and l['itemId'] == existing_values[0])]

            self.chest_loot.append({
                'chestId': chest_id,
                'itemId': item_id,
                'rarity': rarity_var.get(),
                'dropRate': rate_entry.get(),
                'minQuantity': min_entry.get(),
                'maxQuantity': max_entry.get()
            })

            self.refresh_loot_list()
            dialog.destroy()

        ttk.Button(dialog, text="保存", command=save).pack(pady=10)

    def delete_loot(self):
        """删除掉落物品"""
        if not self.current_chest:
            return

        selection = self.loot_tree.selection()
        if not selection:
            messagebox.showwarning("警告", "请先选择一个掉落物品")
            return

        item = self.loot_tree.item(selection[0])
        item_id = item['values'][0]
        chest_id = self.current_chest['id']

        self.chest_loot = [l for l in self.chest_loot
                          if not (l['chestId'] == chest_id and l['itemId'] == item_id)]

        self.refresh_loot_list()

    def save_all(self):
        """保存所有数据"""
        self.save_current_chest()

        # 保存宝箱
        fieldnames = get_fieldnames('chests.csv')
        if not fieldnames:
            fieldnames = ['id', 'name', 'description', 'type', 'respawnSeconds', 'icon']
        write_csv('chests.csv', self.chests, fieldnames)

        # 保存掉落
        fieldnames = get_fieldnames('chest_loot.csv')
        if not fieldnames:
            fieldnames = ['chestId', 'itemId', 'rarity', 'dropRate', 'minQuantity', 'maxQuantity']
        write_csv('chest_loot.csv', self.chest_loot, fieldnames)

        messagebox.showinfo("提示", "宝箱数据已保存")
