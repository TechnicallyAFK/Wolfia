<!--
  - Copyright (C) 2016-2020 Dennis Neufeld
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published
  - by the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
	<div>
		<div class="is-size-1">The team behind Wolfia</div>
		<div id="staff" class="columns is-centered is-multiline">
			<div
				class="member column is-half-tablet is-one-third-desktop"
				v-for="member in staff"
				:key="member.user.discordId"
			>
				<StaffMember :member="member" class="member" />
			</div>
		</div>
	</div>
</template>

<script>
import { mapActions, mapState } from "vuex";
import { FETCH_STAFF } from "@/store/action-types";
import StaffMember from "@/views/staff/StaffMember";

export default {
	name: "Staff",
	components: {
		StaffMember,
	},
	mounted() {
		this.fetchStaff();
	},

	computed: {
		...mapState({
			staff: (state) => [...state.staff].sort((a, b) => a.user.discordId - b.user.discordId),
		}),
	},
	methods: {
		...mapActions({
			fetchStaff: FETCH_STAFF,
		}),
	},
};
</script>

<style scoped>
#staff {
	padding-right: 6em;
	padding-left: 6em;
}
.member {
	padding: 1em;
}
</style>
