#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
礼包编辑器
"""

import tkinter as tk
from tkinter import ttk, messagebox
from csv_utils import read_csv, write_csv, get_fieldnames
from constants import RARITIES


class GiftEditor:
    def __init__(self, parent):
        self.frame = ttk.Frame(parent)
        self.current_gift = None

        self.create_widgets()
        self.load_data()

    def create_widgets(self):
        """创建界面组件"""
        # 左侧 - 礼包列表
        left_panel = ttk.Frame(self.frame, width=200)
        left_panel.pack(side=tk.LEFT, fill=tk.Y, padx=5, pady=5)

        ttk.Label(left_panel, text="礼包列表").pack()

        list_frame = ttk.Frame(left_panel)
        list_frame.pack(fill=tk.BOTH, expand=True)

        self.gift_listbox = tk.Listbox(list_frame, width=25)
        self.gift_listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.gift_listbox.bind('<<ListboxSelect>>', self.on_gift_select)

        scrollbar = ttk.Scrollbar(list_frame, orient=tk.VERTICAL,
                                  command=self.gift_listbox.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.gift_listbox.config(yscrollcommand=scrollbar.set)

        btn_frame = ttk.Frame(left_panel)
        btn_frame.pack(fill=tk.X, pady=5)

        ttk.Button(btn_frame, text="新建礼包", command=self.new_gift).pack(fill=tk.X)
        ttk.Button(btn_frame, text="删除礼包", command=self.delete_gift).pack(fill=tk.X)
        ttk.Button(btn_frame, text="保存所有", command=self.save_all).pack(fill=tk.X)

        # 右侧 - 编辑面板
        right_panel = ttk.Frame(self.frame)
        right_panel.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=5, pady=5)

        # 创建notebook
        self.notebook = ttk.Notebook(right_panel)
        self.notebook.pack(fill=tk.BOTH, expand=True)

        self.create_basic_tab()
        self.create_content_tab()

    def create_basic_tab(self):
        """创建基本信息标签页"""
        tab = ttk.Frame(self.notebook)
        self.notebook.add(tab, text="基本信息")

        self.basic_fields = {}
        labels = [
            ('id', '礼包ID'),
            ('name', '名称'),
            ('description', '描述'),
            ('basePrice', '基础价格')
        ]

        for field, label in labels:
            row = ttk.Frame(tab)
            row.pack(fill=tk.X, padx=10, pady=2)
            ttk.Label(row, text=label, width=15).pack(side=tk.LEFT)
            entry = ttk.Entry(row)
            entry.pack(side=tk.LEFT, fill=tk.X, expand=True)
            self.basic_fields[field] = entry

        # 说明
        ttk.Label(tab, text="说明：").pack(anchor=tk.W, padx=10, pady=(10, 0))
        ttk.Label(tab, text="  礼包是一种特殊物品，使用后获得礼包内配置的所有物品").pack(anchor=tk.W, padx=10)
        ttk.Label(tab, text="  礼包ID需要与物品ID一致，类型为GIFT，效果为OPEN_GIFT").pack(anchor=tk.W, padx=10)
        ttk.Label(tab, text="  使用礼包后，前端会弹出获得物品窗口").pack(anchor=tk.W, padx=10)

    def create_content_tab(self):
        """创建礼包内容标签页"""
        tab = ttk.Frame(self.notebook)
        self.notebook.add(tab, text="礼包内容")

        # 内容物品列表
        list_frame = ttk.Frame(tab)
        list_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=5)

        columns = ('itemId', 'rarity', 'quantity')
        self.content_tree = ttk.Treeview(list_frame, columns=columns, show='headings')
        self.content_tree.heading('itemId', text='物品ID')
        self.content_tree.heading('rarity', text='稀有度')
        self.content_tree.heading('quantity', text='数量')
        self.content_tree.column('itemId', width=150)
        self.content_tree.column('rarity', width=100)
        self.content_tree.column('quantity', width=80)
        self.content_tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        scrollbar = ttk.Scrollbar(list_frame, orient=tk.VERTICAL,
                                  command=self.content_tree.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.content_tree.config(yscrollcommand=scrollbar.set)

        # 操作按钮
        btn_frame = ttk.Frame(tab)
        btn_frame.pack(fill=tk.X, padx=10, pady=5)

        ttk.Button(btn_frame, text="添加物品", command=self.add_content).pack(side=tk.LEFT, padx=2)
        ttk.Button(btn_frame, text="编辑物品", command=self.edit_content).pack(side=tk.LEFT, padx=2)
        ttk.Button(btn_frame, text="删除物品", command=self.delete_content).pack(side=tk.LEFT, padx=2)

    def load_data(self):
        """加载数据"""
        # 从items.csv中筛选出GIFT类型的物品作为礼包
        all_items = read_csv('items.csv')
        self.gifts = [item for item in all_items if item.get('type') == 'GIFT']
        self.all_items = all_items

        # 加载礼包内容
        self.gift_loot = read_csv('gift_loot.csv')

        # 加载可选物品（普通物品和装备）
        self.items = read_csv('items.csv')
        self.equipment = read_csv('equipment.csv')

        self.refresh_gift_list()

    def refresh_gift_list(self):
        """刷新礼包列表"""
        self.gift_listbox.delete(0, tk.END)
        for gift in self.gifts:
            self.gift_listbox.insert(tk.END, f"{gift['id']} - {gift['name']}")

    def on_gift_select(self, event):
        """选择礼包"""
        selection = self.gift_listbox.curselection()
        if selection:
            idx = selection[0]
            self.current_gift = self.gifts[idx]
            self.load_gift_data()

    def load_gift_data(self):
        """加载礼包数据到表单"""
        if not self.current_gift:
            return

        for field, entry in self.basic_fields.items():
            entry.delete(0, tk.END)
            entry.insert(0, self.current_gift.get(field, ''))

        self.refresh_content_list()

    def refresh_content_list(self):
        """刷新礼包内容列表"""
        for item in self.content_tree.get_children():
            self.content_tree.delete(item)

        if not self.current_gift:
            return

        gift_id = self.current_gift['id']
        for loot in self.gift_loot:
            if loot['giftId'] == gift_id:
                self.content_tree.insert('', tk.END, values=(
                    loot['itemId'], loot['rarity'], loot['quantity']
                ))

    def save_current_gift(self):
        """保存当前礼包"""
        if not self.current_gift:
            return

        for field, entry in self.basic_fields.items():
            self.current_gift[field] = entry.get()

        # 确保类型和效果正确
        self.current_gift['type'] = 'GIFT'
        self.current_gift['effect'] = 'OPEN_GIFT'
        self.current_gift['maxStack'] = '1'
        self.current_gift['effectValue'] = ''

        self.refresh_gift_list()

    def new_gift(self):
        """新建礼包"""
        dialog = tk.Toplevel(self.frame)
        dialog.title("新建礼包")
        dialog.geometry("350x200")

        ttk.Label(dialog, text="礼包ID:").pack(pady=5)
        id_entry = ttk.Entry(dialog)
        id_entry.pack(fill=tk.X, padx=20)

        ttk.Label(dialog, text="礼包名称:").pack(pady=5)
        name_entry = ttk.Entry(dialog)
        name_entry.pack(fill=tk.X, padx=20)

        ttk.Label(dialog, text="描述:").pack(pady=5)
        desc_entry = ttk.Entry(dialog)
        desc_entry.pack(fill=tk.X, padx=20)

        def create():
            gift_id = id_entry.get().strip()
            if not gift_id:
                messagebox.showerror("错误", "请输入礼包ID")
                return

            # 检查ID是否已存在
            for item in self.all_items:
                if item['id'] == gift_id:
                    messagebox.showerror("错误", "该ID已存在于物品列表中")
                    return

            new_gift = {
                'id': gift_id,
                'name': name_entry.get() or '新礼包',
                'description': desc_entry.get() or '一个礼包',
                'type': 'GIFT',
                'maxStack': '1',
                'basePrice': '0',
                'effect': 'OPEN_GIFT',
                'effectValue': ''
            }

            self.gifts.append(new_gift)
            self.all_items.append(new_gift)
            self.refresh_gift_list()
            dialog.destroy()

        ttk.Button(dialog, text="创建", command=create).pack(pady=10)

    def delete_gift(self):
        """删除礼包"""
        selection = self.gift_listbox.curselection()
        if not selection:
            messagebox.showwarning("警告", "请先选择一个礼包")
            return

        idx = selection[0]
        gift = self.gifts[idx]

        if not messagebox.askyesno("确认", f"确定要删除礼包 {gift['id']} 吗？\n这将同时从物品列表中删除该礼包。"):
            return

        # 从礼包列表删除
        del self.gifts[idx]

        # 从物品列表删除
        self.all_items = [item for item in self.all_items if item['id'] != gift['id']]

        # 删除相关内容
        self.gift_loot = [l for l in self.gift_loot if l['giftId'] != gift['id']]

        self.current_gift = None
        self.refresh_gift_list()

    def add_content(self):
        """添加礼包内容"""
        if not self.current_gift:
            messagebox.showwarning("警告", "请先选择一个礼包")
            return

        self.edit_content_dialog(None)

    def edit_content(self):
        """编辑礼包内容"""
        if not self.current_gift:
            return

        selection = self.content_tree.selection()
        if not selection:
            messagebox.showwarning("警告", "请先选择一个物品")
            return

        item = self.content_tree.item(selection[0])
        values = item['values']
        self.edit_content_dialog(values)

    def edit_content_dialog(self, existing_values):
        """礼包内容编辑对话框"""
        dialog = tk.Toplevel(self.frame)
        dialog.title("编辑内容" if existing_values else "添加内容")
        dialog.geometry("400x250")

        # 物品选择
        ttk.Label(dialog, text="物品:").pack(pady=5)
        item_var = tk.StringVar()
        # 排除GIFT类型的物品，避免礼包套娃
        all_items = [i['id'] for i in self.items if i.get('type') != 'GIFT'] + [e['id'] for e in self.equipment]
        item_combo = ttk.Combobox(dialog, textvariable=item_var, values=all_items)
        item_combo.pack(fill=tk.X, padx=20)

        # 稀有度
        ttk.Label(dialog, text="稀有度:").pack(pady=5)
        rarity_var = tk.StringVar(value='COMMON')
        rarity_combo = ttk.Combobox(dialog, textvariable=rarity_var,
                                    values=RARITIES,
                                    state='readonly')
        rarity_combo.pack(fill=tk.X, padx=20)

        # 数量
        ttk.Label(dialog, text="数量:").pack(pady=5)
        qty_entry = ttk.Entry(dialog)
        qty_entry.insert(0, '1')
        qty_entry.pack(fill=tk.X, padx=20)

        if existing_values:
            item_var.set(existing_values[0])
            rarity_var.set(existing_values[1])
            qty_entry.delete(0, tk.END)
            qty_entry.insert(0, existing_values[2])

        def save():
            item_id = item_var.get()
            if not item_id:
                messagebox.showerror("错误", "请选择物品")
                return

            gift_id = self.current_gift['id']

            # 如果是编辑，先删除旧的
            if existing_values:
                self.gift_loot = [l for l in self.gift_loot
                                  if not (l['giftId'] == gift_id and l['itemId'] == existing_values[0])]

            self.gift_loot.append({
                'giftId': gift_id,
                'itemId': item_id,
                'rarity': rarity_var.get(),
                'quantity': qty_entry.get()
            })

            self.refresh_content_list()
            dialog.destroy()

        ttk.Button(dialog, text="保存", command=save).pack(pady=10)

    def delete_content(self):
        """删除礼包内容"""
        if not self.current_gift:
            return

        selection = self.content_tree.selection()
        if not selection:
            messagebox.showwarning("警告", "请先选择一个物品")
            return

        item = self.content_tree.item(selection[0])
        item_id = item['values'][0]
        gift_id = self.current_gift['id']

        self.gift_loot = [l for l in self.gift_loot
                         if not (l['giftId'] == gift_id and l['itemId'] == item_id)]

        self.refresh_content_list()

    def save_all(self):
        """保存所有数据"""
        self.save_current_gift()

        # 保存物品（包含礼包）
        fieldnames = get_fieldnames('items.csv')
        if not fieldnames:
            fieldnames = ['id', 'name', 'description', 'type', 'maxStack',
                          'basePrice', 'effect', 'effectValue']
        write_csv('items.csv', self.all_items, fieldnames)

        # 保存礼包内容
        fieldnames = get_fieldnames('gift_loot.csv')
        if not fieldnames:
            fieldnames = ['giftId', 'itemId', 'rarity', 'quantity']
        write_csv('gift_loot.csv', self.gift_loot, fieldnames)

        messagebox.showinfo("提示", "礼包数据已保存")
