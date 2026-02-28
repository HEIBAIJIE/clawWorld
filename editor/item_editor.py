#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
物品编辑器
"""

import tkinter as tk
from tkinter import ttk, messagebox
from csv_utils import read_csv, write_csv, get_fieldnames
from constants import ITEM_TYPES, ITEM_EFFECTS


class ItemEditor:
    def __init__(self, parent):
        self.frame = ttk.Frame(parent)
        self.current_item = None

        self.create_widgets()
        self.load_data()

    def create_widgets(self):
        """创建界面组件"""
        # 左侧 - 物品列表
        left_panel = ttk.Frame(self.frame, width=200)
        left_panel.pack(side=tk.LEFT, fill=tk.Y, padx=5, pady=5)

        ttk.Label(left_panel, text="物品列表").pack()

        list_frame = ttk.Frame(left_panel)
        list_frame.pack(fill=tk.BOTH, expand=True)

        self.item_listbox = tk.Listbox(list_frame, width=25)
        self.item_listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.item_listbox.bind('<<ListboxSelect>>', self.on_item_select)

        scrollbar = ttk.Scrollbar(list_frame, orient=tk.VERTICAL,
                                  command=self.item_listbox.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.item_listbox.config(yscrollcommand=scrollbar.set)

        btn_frame = ttk.Frame(left_panel)
        btn_frame.pack(fill=tk.X, pady=5)

        ttk.Button(btn_frame, text="新建物品", command=self.new_item).pack(fill=tk.X)
        ttk.Button(btn_frame, text="删除物品", command=self.delete_item).pack(fill=tk.X)
        ttk.Button(btn_frame, text="保存所有", command=self.save_all).pack(fill=tk.X)

        # 右侧 - 编辑面板
        right_panel = ttk.Frame(self.frame)
        right_panel.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=5, pady=5)

        self.fields = {}
        labels = [
            ('id', '物品ID'),
            ('name', '名称'),
            ('description', '描述'),
            ('maxStack', '最大堆叠'),
            ('basePrice', '基础价格'),
            ('effectValue', '效果值'),
            ('icon', '图标')
        ]

        for field, label in labels:
            row = ttk.Frame(right_panel)
            row.pack(fill=tk.X, padx=10, pady=2)
            ttk.Label(row, text=label, width=15).pack(side=tk.LEFT)
            entry = ttk.Entry(row)
            entry.pack(side=tk.LEFT, fill=tk.X, expand=True)
            self.fields[field] = entry

        # 物品类型
        type_row = ttk.Frame(right_panel)
        type_row.pack(fill=tk.X, padx=10, pady=2)
        ttk.Label(type_row, text="物品类型", width=15).pack(side=tk.LEFT)
        self.type_var = tk.StringVar(value='CONSUMABLE')
        type_combo = ttk.Combobox(type_row, textvariable=self.type_var,
                                  values=ITEM_TYPES, state='readonly')
        type_combo.pack(side=tk.LEFT, fill=tk.X, expand=True)

        # 效果类型
        effect_row = ttk.Frame(right_panel)
        effect_row.pack(fill=tk.X, padx=10, pady=2)
        ttk.Label(effect_row, text="效果类型", width=15).pack(side=tk.LEFT)
        self.effect_var = tk.StringVar(value='NONE')
        effect_combo = ttk.Combobox(effect_row, textvariable=self.effect_var,
                                    values=ITEM_EFFECTS, state='readonly')
        effect_combo.pack(side=tk.LEFT, fill=tk.X, expand=True)

    def load_data(self):
        """加载数据"""
        self.items = read_csv('items.csv')
        self.refresh_item_list()

    def refresh_item_list(self):
        """刷新物品列表"""
        self.item_listbox.delete(0, tk.END)
        for item in self.items:
            self.item_listbox.insert(tk.END, f"{item['id']} - {item['name']}")

    def on_item_select(self, event):
        """选择物品"""
        selection = self.item_listbox.curselection()
        if selection:
            idx = selection[0]
            self.current_item = self.items[idx]
            self.load_item_data()

    def load_item_data(self):
        """加载物品数据到表单"""
        if not self.current_item:
            return

        for field, entry in self.fields.items():
            entry.delete(0, tk.END)
            entry.insert(0, self.current_item.get(field, ''))

        self.type_var.set(self.current_item.get('type', 'CONSUMABLE'))
        self.effect_var.set(self.current_item.get('effect', 'NONE'))

    def save_current_item(self):
        """保存当前物品"""
        if not self.current_item:
            return

        for field, entry in self.fields.items():
            self.current_item[field] = entry.get()

        self.current_item['type'] = self.type_var.get()
        self.current_item['effect'] = self.effect_var.get()

        self.refresh_item_list()

    def new_item(self):
        """新建物品"""
        dialog = tk.Toplevel(self.frame)
        dialog.title("新建物品")
        dialog.geometry("300x150")

        ttk.Label(dialog, text="物品ID:").pack(pady=5)
        id_entry = ttk.Entry(dialog)
        id_entry.pack(fill=tk.X, padx=20)

        ttk.Label(dialog, text="物品名称:").pack(pady=5)
        name_entry = ttk.Entry(dialog)
        name_entry.pack(fill=tk.X, padx=20)

        def create():
            item_id = id_entry.get().strip()
            if not item_id:
                messagebox.showerror("错误", "请输入物品ID")
                return

            for item in self.items:
                if item['id'] == item_id:
                    messagebox.showerror("错误", "物品ID已存在")
                    return

            new_item = {
                'id': item_id,
                'name': name_entry.get(),
                'description': '',
                'type': 'CONSUMABLE',
                'maxStack': '99',
                'basePrice': '10',
                'effect': 'NONE',
                'effectValue': '0',
                'icon': ''
            }

            self.items.append(new_item)
            self.refresh_item_list()
            dialog.destroy()

        ttk.Button(dialog, text="创建", command=create).pack(pady=10)

    def delete_item(self):
        """删除物品"""
        selection = self.item_listbox.curselection()
        if not selection:
            messagebox.showwarning("警告", "请先选择一个物品")
            return

        idx = selection[0]
        item = self.items[idx]

        if not messagebox.askyesno("确认", f"确定要删除物品 {item['id']} 吗？"):
            return

        del self.items[idx]
        self.current_item = None
        self.refresh_item_list()

    def save_all(self):
        """保存所有数据"""
        self.save_current_item()

        fieldnames = get_fieldnames('items.csv')
        if not fieldnames:
            fieldnames = ['id', 'name', 'description', 'type', 'maxStack',
                          'basePrice', 'effect', 'effectValue', 'icon']
        write_csv('items.csv', self.items, fieldnames)

        messagebox.showinfo("提示", "物品数据已保存")
