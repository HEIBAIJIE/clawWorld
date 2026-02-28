#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
装备编辑器
"""

import tkinter as tk
from tkinter import ttk, messagebox
from csv_utils import read_csv, write_csv, get_fieldnames
from constants import EQUIPMENT_SLOTS, RARITIES, RARITY_COLORS


class EquipmentEditor:
    def __init__(self, parent):
        self.frame = ttk.Frame(parent)
        self.current_equipment = None

        self.create_widgets()
        self.load_data()

    def create_widgets(self):
        """创建界面组件"""
        # 左侧 - 装备列表
        left_panel = ttk.Frame(self.frame, width=200)
        left_panel.pack(side=tk.LEFT, fill=tk.Y, padx=5, pady=5)

        ttk.Label(left_panel, text="装备列表").pack()

        list_frame = ttk.Frame(left_panel)
        list_frame.pack(fill=tk.BOTH, expand=True)

        self.equip_listbox = tk.Listbox(list_frame, width=25)
        self.equip_listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.equip_listbox.bind('<<ListboxSelect>>', self.on_equip_select)

        scrollbar = ttk.Scrollbar(list_frame, orient=tk.VERTICAL,
                                  command=self.equip_listbox.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.equip_listbox.config(yscrollcommand=scrollbar.set)

        btn_frame = ttk.Frame(left_panel)
        btn_frame.pack(fill=tk.X, pady=5)

        ttk.Button(btn_frame, text="新建装备", command=self.new_equipment).pack(fill=tk.X)
        ttk.Button(btn_frame, text="删除装备", command=self.delete_equipment).pack(fill=tk.X)
        ttk.Button(btn_frame, text="保存所有", command=self.save_all).pack(fill=tk.X)

        # 右侧 - 编辑面板
        right_panel = ttk.Frame(self.frame)
        right_panel.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=5, pady=5)

        # 创建notebook
        self.notebook = ttk.Notebook(right_panel)
        self.notebook.pack(fill=tk.BOTH, expand=True)

        self.create_basic_tab()
        self.create_stats_tab()

    def create_basic_tab(self):
        """创建基本信息标签页"""
        tab = ttk.Frame(self.notebook)
        self.notebook.add(tab, text="基本信息")

        self.basic_fields = {}
        labels = [
            ('id', '装备ID'),
            ('name', '名称'),
            ('description', '描述'),
            ('basePrice', '基础价格'),
            ('icon', '图标')
        ]

        for field, label in labels:
            row = ttk.Frame(tab)
            row.pack(fill=tk.X, padx=10, pady=2)
            ttk.Label(row, text=label, width=15).pack(side=tk.LEFT)
            entry = ttk.Entry(row)
            entry.pack(side=tk.LEFT, fill=tk.X, expand=True)
            self.basic_fields[field] = entry

        # 槽位
        slot_row = ttk.Frame(tab)
        slot_row.pack(fill=tk.X, padx=10, pady=2)
        ttk.Label(slot_row, text="装备槽位", width=15).pack(side=tk.LEFT)
        self.slot_var = tk.StringVar(value='RIGHT_HAND')
        slot_combo = ttk.Combobox(slot_row, textvariable=self.slot_var,
                                  values=EQUIPMENT_SLOTS, state='readonly')
        slot_combo.pack(side=tk.LEFT, fill=tk.X, expand=True)

        # 稀有度
        rarity_row = ttk.Frame(tab)
        rarity_row.pack(fill=tk.X, padx=10, pady=2)
        ttk.Label(rarity_row, text="稀有度", width=15).pack(side=tk.LEFT)
        self.rarity_var = tk.StringVar(value='COMMON')
        rarity_combo = ttk.Combobox(rarity_row, textvariable=self.rarity_var,
                                    values=RARITIES, state='readonly')
        rarity_combo.pack(side=tk.LEFT, fill=tk.X, expand=True)

    def create_stats_tab(self):
        """创建属性标签页"""
        tab = ttk.Frame(self.notebook)
        self.notebook.add(tab, text="属性加成")

        self.stat_fields = {}
        labels = [
            ('strength', '力量'),
            ('agility', '敏捷'),
            ('intelligence', '智力'),
            ('vitality', '体力'),
            ('physicalAttack', '物理攻击'),
            ('physicalDefense', '物理防御'),
            ('magicAttack', '法术攻击'),
            ('magicDefense', '法术防御'),
            ('speed', '速度'),
            ('critRate', '暴击率'),
            ('critDamage', '暴击伤害'),
            ('hitRate', '命中率'),
            ('dodgeRate', '闪避率')
        ]

        for field, label in labels:
            row = ttk.Frame(tab)
            row.pack(fill=tk.X, padx=10, pady=2)
            ttk.Label(row, text=label, width=15).pack(side=tk.LEFT)
            entry = ttk.Entry(row)
            entry.pack(side=tk.LEFT, fill=tk.X, expand=True)
            self.stat_fields[field] = entry

    def load_data(self):
        """加载数据"""
        self.equipment = read_csv('equipment.csv')
        self.refresh_equip_list()

    def refresh_equip_list(self):
        """刷新装备列表"""
        self.equip_listbox.delete(0, tk.END)
        for equip in self.equipment:
            self.equip_listbox.insert(tk.END, f"{equip['id']} - {equip['name']}")

    def on_equip_select(self, event):
        """选择装备"""
        selection = self.equip_listbox.curselection()
        if selection:
            idx = selection[0]
            self.current_equipment = self.equipment[idx]
            self.load_equip_data()

    def load_equip_data(self):
        """加载装备数据到表单"""
        if not self.current_equipment:
            return

        for field, entry in self.basic_fields.items():
            entry.delete(0, tk.END)
            entry.insert(0, self.current_equipment.get(field, ''))

        self.slot_var.set(self.current_equipment.get('slot', 'RIGHT_HAND'))
        self.rarity_var.set(self.current_equipment.get('rarity', 'COMMON'))

        for field, entry in self.stat_fields.items():
            entry.delete(0, tk.END)
            entry.insert(0, self.current_equipment.get(field, '0'))

    def save_current_equipment(self):
        """保存当前装备"""
        if not self.current_equipment:
            return

        for field, entry in self.basic_fields.items():
            self.current_equipment[field] = entry.get()

        self.current_equipment['slot'] = self.slot_var.get()
        self.current_equipment['rarity'] = self.rarity_var.get()

        for field, entry in self.stat_fields.items():
            self.current_equipment[field] = entry.get()

        self.refresh_equip_list()

    def new_equipment(self):
        """新建装备"""
        dialog = tk.Toplevel(self.frame)
        dialog.title("新建装备")
        dialog.geometry("300x150")

        ttk.Label(dialog, text="装备ID:").pack(pady=5)
        id_entry = ttk.Entry(dialog)
        id_entry.pack(fill=tk.X, padx=20)

        ttk.Label(dialog, text="装备名称:").pack(pady=5)
        name_entry = ttk.Entry(dialog)
        name_entry.pack(fill=tk.X, padx=20)

        def create():
            equip_id = id_entry.get().strip()
            if not equip_id:
                messagebox.showerror("错误", "请输入装备ID")
                return

            for equip in self.equipment:
                if equip['id'] == equip_id:
                    messagebox.showerror("错误", "装备ID已存在")
                    return

            new_equip = {
                'id': equip_id,
                'name': name_entry.get(),
                'description': '',
                'basePrice': '100',
                'slot': 'RIGHT_HAND',
                'rarity': 'COMMON',
                'strength': '0', 'agility': '0', 'intelligence': '0', 'vitality': '0',
                'physicalAttack': '0', 'physicalDefense': '0',
                'magicAttack': '0', 'magicDefense': '0',
                'speed': '0', 'critRate': '0', 'critDamage': '0',
                'hitRate': '0', 'dodgeRate': '0',
                'icon': ''
            }

            self.equipment.append(new_equip)
            self.refresh_equip_list()
            dialog.destroy()

        ttk.Button(dialog, text="创建", command=create).pack(pady=10)

    def delete_equipment(self):
        """删除装备"""
        selection = self.equip_listbox.curselection()
        if not selection:
            messagebox.showwarning("警告", "请先选择一个装备")
            return

        idx = selection[0]
        equip = self.equipment[idx]

        if not messagebox.askyesno("确认", f"确定要删除装备 {equip['id']} 吗？"):
            return

        del self.equipment[idx]
        self.current_equipment = None
        self.refresh_equip_list()

    def save_all(self):
        """保存所有数据"""
        self.save_current_equipment()

        fieldnames = get_fieldnames('equipment.csv')
        if not fieldnames:
            fieldnames = ['id', 'name', 'description', 'basePrice', 'slot', 'rarity',
                          'strength', 'agility', 'intelligence', 'vitality',
                          'physicalAttack', 'physicalDefense', 'magicAttack', 'magicDefense',
                          'speed', 'critRate', 'critDamage', 'hitRate', 'dodgeRate', 'icon']
        write_csv('equipment.csv', self.equipment, fieldnames)

        messagebox.showinfo("提示", "装备数据已保存")
