#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
NPC编辑器
"""

import tkinter as tk
from tkinter import ttk, messagebox
from csv_utils import read_csv, write_csv, get_fieldnames


class NPCEditor:
    def __init__(self, parent):
        self.frame = ttk.Frame(parent)
        self.current_npc = None

        self.create_widgets()
        self.load_data()

    def create_widgets(self):
        """创建界面组件"""
        # 左侧 - NPC列表
        left_panel = ttk.Frame(self.frame, width=200)
        left_panel.pack(side=tk.LEFT, fill=tk.Y, padx=5, pady=5)

        ttk.Label(left_panel, text="NPC列表").pack()

        list_frame = ttk.Frame(left_panel)
        list_frame.pack(fill=tk.BOTH, expand=True)

        self.npc_listbox = tk.Listbox(list_frame, width=25)
        self.npc_listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.npc_listbox.bind('<<ListboxSelect>>', self.on_npc_select)

        scrollbar = ttk.Scrollbar(list_frame, orient=tk.VERTICAL,
                                  command=self.npc_listbox.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.npc_listbox.config(yscrollcommand=scrollbar.set)

        btn_frame = ttk.Frame(left_panel)
        btn_frame.pack(fill=tk.X, pady=5)

        ttk.Button(btn_frame, text="新建NPC", command=self.new_npc).pack(fill=tk.X)
        ttk.Button(btn_frame, text="删除NPC", command=self.delete_npc).pack(fill=tk.X)
        ttk.Button(btn_frame, text="保存所有", command=self.save_all).pack(fill=tk.X)

        # 右侧 - 编辑面板
        right_panel = ttk.Frame(self.frame)
        right_panel.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=5, pady=5)

        # 创建notebook
        self.notebook = ttk.Notebook(right_panel)
        self.notebook.pack(fill=tk.BOTH, expand=True)

        self.create_basic_tab()
        self.create_shop_tab()

    def create_basic_tab(self):
        """创建基本信息标签页"""
        tab = ttk.Frame(self.notebook)
        self.notebook.add(tab, text="基本信息")

        self.basic_fields = {}
        labels = [
            ('id', 'NPC ID'),
            ('name', '名称'),
            ('description', '描述'),
            ('dialogues', '对话(分号分隔)'),
            ('shopGold', '商店金币'),
            ('shopRefreshSeconds', '刷新时间(秒)'),
            ('priceMultiplier', '价格倍率'),
            ('walkSprite', '行走图'),
            ('portrait', '头像')
        ]

        for field, label in labels:
            row = ttk.Frame(tab)
            row.pack(fill=tk.X, padx=10, pady=2)
            ttk.Label(row, text=label, width=15).pack(side=tk.LEFT)
            entry = ttk.Entry(row)
            entry.pack(side=tk.LEFT, fill=tk.X, expand=True)
            self.basic_fields[field] = entry

        # 复选框
        self.has_shop_var = tk.BooleanVar()
        ttk.Checkbutton(tab, text="有商店", variable=self.has_shop_var).pack(anchor=tk.W, padx=10)

        self.has_dialogue_var = tk.BooleanVar()
        ttk.Checkbutton(tab, text="有对话", variable=self.has_dialogue_var).pack(anchor=tk.W, padx=10)

    def create_shop_tab(self):
        """创建商店标签页"""
        tab = ttk.Frame(self.notebook)
        self.notebook.add(tab, text="商店物品")

        # 商店物品列表
        list_frame = ttk.Frame(tab)
        list_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=5)

        columns = ('itemId', 'quantity')
        self.shop_tree = ttk.Treeview(list_frame, columns=columns, show='headings')
        self.shop_tree.heading('itemId', text='物品ID')
        self.shop_tree.heading('quantity', text='数量')
        self.shop_tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)

        scrollbar = ttk.Scrollbar(list_frame, orient=tk.VERTICAL,
                                  command=self.shop_tree.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.shop_tree.config(yscrollcommand=scrollbar.set)

        # 操作按钮
        btn_frame = ttk.Frame(tab)
        btn_frame.pack(fill=tk.X, padx=10, pady=5)

        ttk.Button(btn_frame, text="添加物品", command=self.add_shop_item).pack(side=tk.LEFT, padx=2)
        ttk.Button(btn_frame, text="编辑物品", command=self.edit_shop_item).pack(side=tk.LEFT, padx=2)
        ttk.Button(btn_frame, text="删除物品", command=self.delete_shop_item).pack(side=tk.LEFT, padx=2)

    def load_data(self):
        """加载数据"""
        self.npcs = read_csv('npcs.csv')
        self.shop_items = read_csv('npc_shop_items.csv')
        self.items = read_csv('items.csv')
        self.equipment = read_csv('equipment.csv')

        self.refresh_npc_list()

    def refresh_npc_list(self):
        """刷新NPC列表"""
        self.npc_listbox.delete(0, tk.END)
        for npc in self.npcs:
            self.npc_listbox.insert(tk.END, f"{npc['id']} - {npc['name']}")

    def on_npc_select(self, event):
        """选择NPC"""
        selection = self.npc_listbox.curselection()
        if selection:
            idx = selection[0]
            self.current_npc = self.npcs[idx]
            self.load_npc_data()

    def load_npc_data(self):
        """加载NPC数据到表单"""
        if not self.current_npc:
            return

        for field, entry in self.basic_fields.items():
            entry.delete(0, tk.END)
            entry.insert(0, self.current_npc.get(field, ''))

        self.has_shop_var.set(self.current_npc.get('hasShop', 'false').lower() == 'true')
        self.has_dialogue_var.set(self.current_npc.get('hasDialogue', 'false').lower() == 'true')

        self.refresh_shop_list()

    def refresh_shop_list(self):
        """刷新商店物品列表"""
        for item in self.shop_tree.get_children():
            self.shop_tree.delete(item)

        if not self.current_npc:
            return

        npc_id = self.current_npc['id']
        for shop_item in self.shop_items:
            if shop_item['npcId'] == npc_id:
                self.shop_tree.insert('', tk.END, values=(
                    shop_item['itemId'], shop_item['quantity']
                ))

    def save_current_npc(self):
        """保存当前NPC"""
        if not self.current_npc:
            return

        for field, entry in self.basic_fields.items():
            self.current_npc[field] = entry.get()

        self.current_npc['hasShop'] = str(self.has_shop_var.get()).lower()
        self.current_npc['hasDialogue'] = str(self.has_dialogue_var.get()).lower()

        self.refresh_npc_list()

    def new_npc(self):
        """新建NPC"""
        dialog = tk.Toplevel(self.frame)
        dialog.title("新建NPC")
        dialog.geometry("300x150")

        ttk.Label(dialog, text="NPC ID:").pack(pady=5)
        id_entry = ttk.Entry(dialog)
        id_entry.pack(fill=tk.X, padx=20)

        ttk.Label(dialog, text="NPC名称:").pack(pady=5)
        name_entry = ttk.Entry(dialog)
        name_entry.pack(fill=tk.X, padx=20)

        def create():
            npc_id = id_entry.get().strip()
            if not npc_id:
                messagebox.showerror("错误", "请输入NPC ID")
                return

            for npc in self.npcs:
                if npc['id'] == npc_id:
                    messagebox.showerror("错误", "NPC ID已存在")
                    return

            new_npc = {
                'id': npc_id,
                'name': name_entry.get(),
                'description': '',
                'hasShop': 'false',
                'hasDialogue': 'true',
                'dialogues': '你好，旅行者！',
                'shopGold': '1000',
                'shopRefreshSeconds': '3600',
                'priceMultiplier': '1.0',
                'walkSprite': '',
                'portrait': ''
            }

            self.npcs.append(new_npc)
            self.refresh_npc_list()
            dialog.destroy()

        ttk.Button(dialog, text="创建", command=create).pack(pady=10)

    def delete_npc(self):
        """删除NPC"""
        selection = self.npc_listbox.curselection()
        if not selection:
            messagebox.showwarning("警告", "请先选择一个NPC")
            return

        idx = selection[0]
        npc = self.npcs[idx]

        if not messagebox.askyesno("确认", f"确定要删除NPC {npc['id']} 吗？"):
            return

        # 删除NPC
        del self.npcs[idx]

        # 删除相关商店物品
        self.shop_items = [s for s in self.shop_items if s['npcId'] != npc['id']]

        self.current_npc = None
        self.refresh_npc_list()

    def add_shop_item(self):
        """添加商店物品"""
        if not self.current_npc:
            messagebox.showwarning("警告", "请先选择一个NPC")
            return

        self.edit_shop_item_dialog(None)

    def edit_shop_item(self):
        """编辑商店物品"""
        if not self.current_npc:
            return

        selection = self.shop_tree.selection()
        if not selection:
            messagebox.showwarning("警告", "请先选择一个物品")
            return

        item = self.shop_tree.item(selection[0])
        values = item['values']
        self.edit_shop_item_dialog(values)

    def edit_shop_item_dialog(self, existing_values):
        """商店物品编辑对话框"""
        dialog = tk.Toplevel(self.frame)
        dialog.title("编辑商店物品" if existing_values else "添加商店物品")
        dialog.geometry("350x150")

        # 物品选择
        ttk.Label(dialog, text="物品:").pack(pady=5)
        item_var = tk.StringVar()
        all_items = [i['id'] for i in self.items] + [e['id'] for e in self.equipment]
        item_combo = ttk.Combobox(dialog, textvariable=item_var, values=all_items)
        item_combo.pack(fill=tk.X, padx=20)

        # 数量
        ttk.Label(dialog, text="数量:").pack(pady=5)
        qty_entry = ttk.Entry(dialog)
        qty_entry.pack(fill=tk.X, padx=20)

        if existing_values:
            item_var.set(existing_values[0])
            qty_entry.insert(0, existing_values[1])

        def save():
            item_id = item_var.get()
            if not item_id:
                messagebox.showerror("错误", "请选择物品")
                return

            npc_id = self.current_npc['id']

            # 如果是编辑，先删除旧的
            if existing_values:
                self.shop_items = [s for s in self.shop_items
                                   if not (s['npcId'] == npc_id and s['itemId'] == existing_values[0])]

            self.shop_items.append({
                'npcId': npc_id,
                'itemId': item_id,
                'quantity': qty_entry.get()
            })

            self.refresh_shop_list()
            dialog.destroy()

        ttk.Button(dialog, text="保存", command=save).pack(pady=10)

    def delete_shop_item(self):
        """删除商店物品"""
        if not self.current_npc:
            return

        selection = self.shop_tree.selection()
        if not selection:
            messagebox.showwarning("警告", "请先选择一个物品")
            return

        item = self.shop_tree.item(selection[0])
        item_id = item['values'][0]
        npc_id = self.current_npc['id']

        self.shop_items = [s for s in self.shop_items
                          if not (s['npcId'] == npc_id and s['itemId'] == item_id)]

        self.refresh_shop_list()

    def save_all(self):
        """保存所有数据"""
        self.save_current_npc()

        # 保存NPC
        fieldnames = get_fieldnames('npcs.csv')
        if not fieldnames:
            fieldnames = ['id', 'name', 'description', 'hasShop', 'hasDialogue',
                          'dialogues', 'shopGold', 'shopRefreshSeconds', 'priceMultiplier',
                          'walkSprite', 'portrait']
        write_csv('npcs.csv', self.npcs, fieldnames)

        # 保存商店物品
        fieldnames = get_fieldnames('npc_shop_items.csv')
        if not fieldnames:
            fieldnames = ['npcId', 'itemId', 'quantity']
        write_csv('npc_shop_items.csv', self.shop_items, fieldnames)

        messagebox.showinfo("提示", "NPC数据已保存")
