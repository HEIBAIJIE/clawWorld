#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
技能编辑器
"""

import tkinter as tk
from tkinter import ttk, messagebox
from csv_utils import read_csv, write_csv, get_fieldnames
from constants import TARGET_TYPES, DAMAGE_TYPES


class SkillEditor:
    def __init__(self, parent):
        self.frame = ttk.Frame(parent)
        self.current_skill = None

        self.create_widgets()
        self.load_data()

    def create_widgets(self):
        """创建界面组件"""
        # 左侧 - 技能列表
        left_panel = ttk.Frame(self.frame, width=200)
        left_panel.pack(side=tk.LEFT, fill=tk.Y, padx=5, pady=5)

        ttk.Label(left_panel, text="技能列表").pack()

        list_frame = ttk.Frame(left_panel)
        list_frame.pack(fill=tk.BOTH, expand=True)

        self.skill_listbox = tk.Listbox(list_frame, width=25)
        self.skill_listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        self.skill_listbox.bind('<<ListboxSelect>>', self.on_skill_select)

        scrollbar = ttk.Scrollbar(list_frame, orient=tk.VERTICAL,
                                  command=self.skill_listbox.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.skill_listbox.config(yscrollcommand=scrollbar.set)

        btn_frame = ttk.Frame(left_panel)
        btn_frame.pack(fill=tk.X, pady=5)

        ttk.Button(btn_frame, text="新建技能", command=self.new_skill).pack(fill=tk.X)
        ttk.Button(btn_frame, text="删除技能", command=self.delete_skill).pack(fill=tk.X)
        ttk.Button(btn_frame, text="保存所有", command=self.save_all).pack(fill=tk.X)

        # 右侧 - 编辑面板
        right_panel = ttk.Frame(self.frame)
        right_panel.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=5, pady=5)

        self.fields = {}
        labels = [
            ('id', '技能ID'),
            ('name', '名称'),
            ('description', '描述'),
            ('manaCost', '法力消耗'),
            ('cooldown', '冷却回合'),
            ('damageMultiplier', '伤害倍率'),
            ('vfx', '特效')
        ]

        for field, label in labels:
            row = ttk.Frame(right_panel)
            row.pack(fill=tk.X, padx=10, pady=2)
            ttk.Label(row, text=label, width=15).pack(side=tk.LEFT)
            entry = ttk.Entry(row)
            entry.pack(side=tk.LEFT, fill=tk.X, expand=True)
            self.fields[field] = entry

        # 目标类型
        target_row = ttk.Frame(right_panel)
        target_row.pack(fill=tk.X, padx=10, pady=2)
        ttk.Label(target_row, text="目标类型", width=15).pack(side=tk.LEFT)
        self.target_var = tk.StringVar(value='ENEMY_SINGLE')
        target_combo = ttk.Combobox(target_row, textvariable=self.target_var,
                                    values=TARGET_TYPES, state='readonly')
        target_combo.pack(side=tk.LEFT, fill=tk.X, expand=True)

        # 伤害类型
        damage_row = ttk.Frame(right_panel)
        damage_row.pack(fill=tk.X, padx=10, pady=2)
        ttk.Label(damage_row, text="伤害类型", width=15).pack(side=tk.LEFT)
        self.damage_var = tk.StringVar(value='PHYSICAL')
        damage_combo = ttk.Combobox(damage_row, textvariable=self.damage_var,
                                    values=DAMAGE_TYPES, state='readonly')
        damage_combo.pack(side=tk.LEFT, fill=tk.X, expand=True)

    def load_data(self):
        """加载数据"""
        self.skills = read_csv('skills.csv')
        self.refresh_skill_list()

    def refresh_skill_list(self):
        """刷新技能列表"""
        self.skill_listbox.delete(0, tk.END)
        for skill in self.skills:
            self.skill_listbox.insert(tk.END, f"{skill['id']} - {skill['name']}")

    def on_skill_select(self, event):
        """选择技能"""
        selection = self.skill_listbox.curselection()
        if selection:
            idx = selection[0]
            self.current_skill = self.skills[idx]
            self.load_skill_data()

    def load_skill_data(self):
        """加载技能数据到表单"""
        if not self.current_skill:
            return

        for field, entry in self.fields.items():
            entry.delete(0, tk.END)
            entry.insert(0, self.current_skill.get(field, ''))

        self.target_var.set(self.current_skill.get('targetType', 'ENEMY_SINGLE'))
        self.damage_var.set(self.current_skill.get('damageType', 'PHYSICAL'))

    def save_current_skill(self):
        """保存当前技能"""
        if not self.current_skill:
            return

        for field, entry in self.fields.items():
            self.current_skill[field] = entry.get()

        self.current_skill['targetType'] = self.target_var.get()
        self.current_skill['damageType'] = self.damage_var.get()

        self.refresh_skill_list()

    def new_skill(self):
        """新建技能"""
        dialog = tk.Toplevel(self.frame)
        dialog.title("新建技能")
        dialog.geometry("300x150")

        ttk.Label(dialog, text="技能ID:").pack(pady=5)
        id_entry = ttk.Entry(dialog)
        id_entry.pack(fill=tk.X, padx=20)

        ttk.Label(dialog, text="技能名称:").pack(pady=5)
        name_entry = ttk.Entry(dialog)
        name_entry.pack(fill=tk.X, padx=20)

        def create():
            skill_id = id_entry.get().strip()
            if not skill_id:
                messagebox.showerror("错误", "请输入技能ID")
                return

            for skill in self.skills:
                if skill['id'] == skill_id:
                    messagebox.showerror("错误", "技能ID已存在")
                    return

            new_skill = {
                'id': skill_id,
                'name': name_entry.get(),
                'description': '',
                'targetType': 'ENEMY_SINGLE',
                'damageType': 'PHYSICAL',
                'manaCost': '10',
                'cooldown': '0',
                'damageMultiplier': '1.0',
                'vfx': ''
            }

            self.skills.append(new_skill)
            self.refresh_skill_list()
            dialog.destroy()

        ttk.Button(dialog, text="创建", command=create).pack(pady=10)

    def delete_skill(self):
        """删除技能"""
        selection = self.skill_listbox.curselection()
        if not selection:
            messagebox.showwarning("警告", "请先选择一个技能")
            return

        idx = selection[0]
        skill = self.skills[idx]

        if not messagebox.askyesno("确认", f"确定要删除技能 {skill['id']} 吗？"):
            return

        del self.skills[idx]
        self.current_skill = None
        self.refresh_skill_list()

    def save_all(self):
        """保存所有数据"""
        self.save_current_skill()

        fieldnames = get_fieldnames('skills.csv')
        if not fieldnames:
            fieldnames = ['id', 'name', 'description', 'targetType', 'damageType',
                          'manaCost', 'cooldown', 'damageMultiplier', 'vfx']
        write_csv('skills.csv', self.skills, fieldnames)

        messagebox.showinfo("提示", "技能数据已保存")
